package com.sap.sse.common.scalablevalue;

import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class KadaneExtremeSubsequenceFinderImpl<ValueType, AveragesTo extends Comparable<AveragesTo>, T extends ComparableScalableValueWithDistance<ValueType, AveragesTo>>
        implements KadaneExtremeSubsequenceFinder<ValueType, AveragesTo, T> {

    private static final long serialVersionUID = -8986609116472739636L;

    private Node<ValueType, AveragesTo, T> first;
    
    private Node<ValueType, AveragesTo, T> last;

    private int size;

    /**
     * Nodes of this type are used to construct a doubly-linked list, with each node holding a reference to the node
     * forming the start of the sequence with the maximum sum.
     * 
     * @author Axel Uhl (d043530)
     */
    private static class Node<ValueType, AveragesTo extends Comparable<AveragesTo>, T extends ComparableScalableValueWithDistance<ValueType, AveragesTo>> {
        private final T value;
        private Node<ValueType, AveragesTo, T> previous;
        private Node<ValueType, AveragesTo, T> next;
        private ScalableValueWithDistance<ValueType, AveragesTo> minSumEndingHere;
        private Node<ValueType, AveragesTo, T> startOfMinSumSubSequenceEndingHere;
        private ScalableValueWithDistance<ValueType, AveragesTo> maxSumEndingHere;
        private Node<ValueType, AveragesTo, T> startOfMaxSumSubSequenceEndingHere;

        private Node(Node<ValueType, AveragesTo, T> previous, Node<ValueType, AveragesTo, T> next, T value) {
            super();
            this.previous = previous;
            this.next = next;
            this.value = value;
            this.minSumEndingHere = null;
            this.startOfMinSumSubSequenceEndingHere = null;
            this.maxSumEndingHere = null;
            this.startOfMaxSumSubSequenceEndingHere = null;
        }

        private Node<ValueType, AveragesTo, T> getPrevious() {
            return previous;
        }

        private Node<ValueType, AveragesTo, T> getNext() {
            return next;
        }

        private void setPrevious(Node<ValueType, AveragesTo, T> previous) {
            this.previous = previous;
        }

        private void setNext(Node<ValueType, AveragesTo, T> next) {
            this.next = next;
        }

        private T getValue() {
            return value;
        }

        private ScalableValueWithDistance<ValueType, AveragesTo> getMinSumEndingHere() {
            return minSumEndingHere;
        }

        private Node<ValueType, AveragesTo, T> getStartOfMinSumSubSequenceEndingHere() {
            return startOfMinSumSubSequenceEndingHere;
        }

        private void setMinSumEndingHere(ScalableValueWithDistance<ValueType, AveragesTo> minSumEndingHere) {
            this.minSumEndingHere = minSumEndingHere;
        }

        private void setStartOfMinSumSubSequenceEndingHere(Node<ValueType, AveragesTo, T> startOfMinSumSubSequenceEndingHere) {
            this.startOfMinSumSubSequenceEndingHere = startOfMinSumSubSequenceEndingHere;
        }

        private void setMaxSumEndingHere(ScalableValueWithDistance<ValueType, AveragesTo> maxSumEndingHere) {
            this.maxSumEndingHere = maxSumEndingHere;
        }

        private void setStartOfMaxSumSubSequenceEndingHere(Node<ValueType, AveragesTo, T> startOfMaxSumSubSequenceEndingHere) {
            this.startOfMaxSumSubSequenceEndingHere = startOfMaxSumSubSequenceEndingHere;
        }

        private ScalableValueWithDistance<ValueType, AveragesTo> getMaxSumEndingHere() {
            return maxSumEndingHere;
        }

        private Node<ValueType, AveragesTo, T> getStartOfMaxSumSubSequenceEndingHere() {
            return startOfMaxSumSubSequenceEndingHere;
        }
        
        /**
         * Updates this node's extreme sum values of the sub-sequences ending at this node, considering the values
         * stored in the {@link #getPrevious() previous} element, if such an element exists. Furthermore, the
         * references to the nodes where these sub-sequences start are updated accordingly.
         * 
         * @return whether the node has changed during this update; this may mean a change in the min/max sum and/or the
         *         the start of the extreme sub-sequence(s) ending at this node. Any such change requires updating
         *         {@link #getNext() following nodes} too.
         */
        private boolean updateThisFromPrevious() {
            final boolean changedByMax = updateMaxFromPrevious();
            final boolean changedByMin = updateMinFromPrevious();
            return changedByMax || changedByMin;
        }

        /**
         * Updates this node's min sum values of the sub-sequences ending at this node, considering the values stored in
         * the {@link #getPrevious() previous} element, if such an element exists. Furthermore, the references to the
         * node where the min sum sub-sequence starts is updated accordingly.
         * 
         * @return whether the node's min sum-related properties changed during this update; this may mean a change in
         *         the min sum and/or the the start of the min sum sub-sequence(s) ending at this node. Any such change
         *         requires updating {@link #getNext() following nodes} using this method, too. It does not happen
         *         automatically by calling this method. This method updates only this node.
         */
        private boolean updateMinFromPrevious() {
            return updateThisFromPrevious(Node::getMinSumEndingHere,
                    Node::getStartOfMinSumSubSequenceEndingHere, this::setMinSumEndingHere,
                    this::setStartOfMinSumSubSequenceEndingHere, (a, b)->compare(b, a));
        }

        /**
         * Updates this node's max sum values of the sub-sequences ending at this node, considering the values stored in
         * the {@link #getPrevious() previous} element, if such an element exists. Furthermore, the references to the
         * node where the max sum sub-sequence starts is updated accordingly.
         * 
         * @return whether the node's max sum-related properties changed during this update; this may mean a change in
         *         the max sum and/or the the start of the max sum sub-sequence(s) ending at this node. Any such change
         *         requires updating {@link #getNext() following nodes} using this method, too. It does not happen
         *         automatically by calling this method. This method updates only this node.
         */
        private boolean updateMaxFromPrevious() {
            return updateThisFromPrevious(Node::getMaxSumEndingHere,
                    Node::getStartOfMaxSumSubSequenceEndingHere, this::setMaxSumEndingHere,
                    this::setStartOfMaxSumSubSequenceEndingHere, this::compare);
        }

        private boolean updateThisFromPrevious(Function<Node<ValueType, AveragesTo, T>, ScalableValueWithDistance<ValueType, AveragesTo>> getExtremeSumEndingHere,
                Function<Node<ValueType, AveragesTo, T>, Node<ValueType, AveragesTo, T>> getStartOfExtremeSumSubSequenceEndingHere,
                Consumer<ScalableValueWithDistance<ValueType, AveragesTo>> setExtremeSumEndingHere,
                Consumer<Node<ValueType, AveragesTo, T>> setStartOfExtremeSubSubSequenceEndingHere,
                BiFunction<ScalableValueWithDistance<ValueType, AveragesTo>, ScalableValueWithDistance<ValueType, AveragesTo>, Integer> comparator) {
            boolean changed = false;
            final ScalableValueWithDistance<ValueType, AveragesTo> newMaxSumEndingAtIndex;
            final Node<ValueType, AveragesTo, T> newStartOfMaxSumSubSequenceEndingHere;
            final ScalableValueWithDistance<ValueType, AveragesTo> sumWithMax = getPrevious() == null ? null : getValue().add(getExtremeSumEndingHere.apply(getPrevious()));
            if (getPrevious() == null || comparator.apply(getValue(), sumWithMax) >= 0) {
                newMaxSumEndingAtIndex = getValue(); // one-element sum consisting of element at "index" is the maximum
                newStartOfMaxSumSubSequenceEndingHere = this;
            } else {
                newMaxSumEndingAtIndex = sumWithMax;
                newStartOfMaxSumSubSequenceEndingHere = getStartOfExtremeSumSubSequenceEndingHere.apply(getPrevious());
            }
            if (!newMaxSumEndingAtIndex.equals(getExtremeSumEndingHere.apply(this))) {
                changed = true;
                setExtremeSumEndingHere.accept(newMaxSumEndingAtIndex);
            }
            if (newStartOfMaxSumSubSequenceEndingHere != getStartOfExtremeSumSubSequenceEndingHere.apply(this)) {
                changed = true;
                setStartOfExtremeSubSubSequenceEndingHere.accept(newStartOfMaxSumSubSequenceEndingHere);
            }
            return changed;
        }

        private Integer compare(final ScalableValueWithDistance<ValueType, AveragesTo> a, final ScalableValueWithDistance<ValueType, AveragesTo> b) {
            return a.divide(1).compareTo(b.divide(1));
        }
    }

    public KadaneExtremeSubsequenceFinderImpl() {
        this.size = 0;
        this.first = null;
        this.last = null;
    }
    
    @Override
    public int size() {
        return size;
    }
    
    @Override
    public boolean isEmpty() {
        return first == null;
    }
    
    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Node<ValueType, AveragesTo, T> current = first;
            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public T next() {
                final T result = current.getValue();
                current = current.getNext();
                return result;
            }
        };
    }

    @Override
    public void add(int index, T t) {
        if (index < 0 || index > size()) {
            throw new IndexOutOfBoundsException("Trying to add at index "+index+" to a sequence of size "+size());
        }
        if (isEmpty()) {
            final Node<ValueType, AveragesTo, T> node = new Node<>(/* previous */ null, /* next */ null, t);
            first = node;
            last = node;
            node.updateThisFromPrevious(); // no need to consider changes; it's the only element
        } else {
            final Node<ValueType, AveragesTo, T> nodeBeforeIndex;
            final Node<ValueType, AveragesTo, T> nodeAfterIndex;
            if (index>0) {
                nodeBeforeIndex = getNode(index-1);
                nodeAfterIndex = nodeBeforeIndex.getNext();
            } else if (index<size()) {
                nodeAfterIndex = getNode(index);
                nodeBeforeIndex = nodeAfterIndex.getPrevious();
            } else {
                throw new InternalError("This shouldn't have happened as it means the sequence is empty, and we shouldn't have arrived in this branch");
            }
            final Node<ValueType, AveragesTo, T> node = new Node<>(nodeBeforeIndex, nodeAfterIndex, t);
            if (nodeBeforeIndex != null) {
                nodeBeforeIndex.setNext(node);
            }
            if (nodeAfterIndex != null) {
                nodeAfterIndex.setPrevious(node);
            }
            node.updateThisFromPrevious();
            if (nodeBeforeIndex == null
                    || !node.getMaxSumEndingHere().equals(nodeBeforeIndex.getMaxSumEndingHere())
                    || !node.getMinSumEndingHere().equals(nodeBeforeIndex.getMinSumEndingHere())
                    || node.getStartOfMaxSumSubSequenceEndingHere() != nodeBeforeIndex.getStartOfMaxSumSubSequenceEndingHere()
                    || node.getStartOfMinSumSubSequenceEndingHere().equals(nodeBeforeIndex.getStartOfMinSumSubSequenceEndingHere())) {
                // the inserted node differs from the previous node in one of the extreme sums ending at it, and/or
                // regarding where those sub-sequences start, so we need to propagate the updates to subsequent nodes
                propagateChanges(node);
            }
        }
    }

    private void propagateChanges(Node<ValueType, AveragesTo, T> node) {
        Node<ValueType, AveragesTo, T> current = node.getNext();
        boolean changedMin = true;
        boolean changedMax = true;
        while ((changedMin || changedMax) && current != null) {
            if (changedMin) {
                changedMin = current.updateMinFromPrevious();
            }
            if (changedMax) {
                changedMax = current.updateMaxFromPrevious();
            }
            current = current.getNext();
        }
    }

    Node<ValueType, AveragesTo, T> getNode(int index) {
        final Node<ValueType, AveragesTo, T> result;
        if (isEmpty()) {
            result = null;
        } else {
            if (index > size()/2) { // search from the end
                result = step(last, size()-1-index, Node::getPrevious);
            } else { // search from the beginning
                result = step(first, index, Node::getNext);
            }
        }
        return result;
    }
    
    private Node<ValueType, AveragesTo, T> step(Node<ValueType, AveragesTo, T> start, int numberOfSteps,
            Function<Node<ValueType, AveragesTo, T>, Node<ValueType, AveragesTo, T>> stepper) {
        Node<ValueType, AveragesTo, T> current = start;
        for (int i=0; i<numberOfSteps; i++) {
            current = stepper.apply(current);
        }
        return current;
    }
    
    @Override
    public void remove(int index) {
        // TODO Auto-generated method stub

    }

    @Override
    public void remove(T t) {
        // TODO Auto-generated method stub

    }

    @Override
    public ScalableValueWithDistance<ValueType, AveragesTo> getMinSum() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ScalableValueWithDistance<ValueType, AveragesTo> getMaxSum() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getStartIndexOfMaxSumSequence() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getEndIndexOfMaxSumSequence() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getStartIndexOfMinSumSequence() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getEndIndexOfMinSumSequence() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Iterator<T> getSubSequenceWithMaxSum() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Iterator<T> getSubSequenceWithMinSum() {
        // TODO Auto-generated method stub
        return null;
    }
}
