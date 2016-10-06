package org.unipop.process.local;

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalParent;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.GroupStep;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.unipop.process.UniBulkStep;
import org.unipop.process.vertex.UniGraphVertexStep;
import org.unipop.query.aggregation.LocalQuery;
import org.unipop.query.search.SearchVertexQuery;
import org.unipop.structure.UniGraph;
import org.unipop.structure.UniVertex;

import java.util.*;

/**
 * Created by sbarzilay on 10/6/16.
 */
public class UniGraphGroupStep<S,K,V> extends UniBulkStep<S, Map<K, V>> implements TraversalParent {

    private final List<LocalQuery.LocalController> controllers;
    private final List<SearchVertexQuery.SearchVertexController> nonLocalControllers;
    private UniGraphLocalStep<S,K> keyStep;
    private UniGraphLocalStep<S,V> valueStep;

    public UniGraphGroupStep(Traversal.Admin traversal, UniGraph graph, List<LocalQuery.LocalController> controllers, List<SearchVertexQuery.SearchVertexController> nonLocalControllers, GroupStep<S, K, V> groupStep) {
        super(traversal, graph);
        this.controllers = controllers;
        this.nonLocalControllers = nonLocalControllers;
        List<Traversal.Admin<?, ?>> localChildren = groupStep.getLocalChildren();
        keyStep = createLocalStep((Traversal.Admin<S, K>)localChildren.get(0));
        valueStep = createLocalStep((Traversal.Admin<S, V>)localChildren.get(1));
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return Collections.singleton(TraverserRequirement.PATH);
    }

    protected <T> UniGraphLocalStep<S,T> createLocalStep(Traversal.Admin<S, T> traversal){
        traversal.getSteps().stream().filter(step -> step instanceof UniGraphVertexStep).forEach(step -> ((UniGraphVertexStep) step).setControllers(nonLocalControllers));
        traversal.setParent(this);
        return new UniGraphLocalStep<>(this.traversal, traversal, controllers);
    }

    @Override
    public List<Traversal.Admin<?, ?>> getLocalChildren() {
        final List<Traversal.Admin<?, ?>> children = new ArrayList<>();
        children.addAll(keyStep.getLocalChildren());
        children.addAll(valueStep.getLocalChildren());
        return children;
    }

    @Override
    protected Iterator<Traverser.Admin<Map<K, V>>> process(List<Traverser.Admin<S>> traversers) {
        keyStep.reset();
        valueStep.reset();
        valueStep.addStarts(traversers.iterator());
        keyStep.addStarts(traversers.iterator());
        List<Traverser.Admin<V>> values = new ArrayList<>();
        valueStep.forEachRemaining(values::add);
        List<Traverser.Admin<K>> keys = new ArrayList<>();
        keyStep.forEachRemaining(keys::add);
        Map<K,V> map = new HashMap<>();
        keys.forEach(key -> {
            UniVertex prev = key.getSideEffects().get("prev");
            Optional<Traverser.Admin<V>> mapValue = values.stream().filter(value -> value.getSideEffects().get("prev").equals(prev)).findFirst();
            map.put(key.get(), mapValue.get().get());
        });
        Traverser.Admin<Map<K, V>> generate = getTraversal().getTraverserGenerator().generate(map, (Step<Map<K, V>, Map<K, V>>) this, 1l);
        return Collections.singleton(generate).iterator();
    }
}
