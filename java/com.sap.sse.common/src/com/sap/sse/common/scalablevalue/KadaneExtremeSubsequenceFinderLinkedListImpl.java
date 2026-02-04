package com.sap.sse.common.scalablevalue;

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * This implementation uses {@link LinkedList}s to implement the full sequence and the max/min sum and start indices
 * collection. This requires index manipulations when elements are inserted to or removed from anywhere but the end of
 * the sequence.
 * 
 * @author Axel Uhl (d043530)
 *
 */
public class KadaneExtremeSubsequenceFinderLinkedListImpl<ValueType, AveragesTo extends Comparable<AveragesTo>, T extends ComparableScalableValueWithDistance<ValueType, AveragesTo>>
implements KadaneExtremeSubsequenceFinder<ValueType, AveragesTo, T>, Serializable, Iterable<T> {
    private static final long serialVersionUID = 2109193559337714286L;
    
    /**
     * The elements constituting the full sequence in which to find the contiguous sub-sequences
     */
    private final List<T> sequence;
    
    /**
     * The element at index <tt>i</tt> holds the maximum value of the sum of any contiguous sub-sequence ending at index
     * <tt>i</tt>. Outside of {@code synchronized} blocks it holds as many elements as {@link #sequence}. The element
     * at index {@code i} is computed as {@code maxSumEndingAt.get(i-1)+sequence.get(i), max(sequence.get(i))}. This covers
     * the two cases extending the complete induction. Either, the sequence with the maximum sum ending at index {@code i}
     * includes prior elements; or the single element {@code sequence.get(i)} is greater than the sum of it and the maximum
     * sum ending at the previous element {@code i-1}.
     */
    private final List<ScalableValueWithDistance<ValueType, AveragesTo>> maxSumEndingAt;
    
    /**
     * Indices of the first element in {@link #sequence} of the contiguous sub-sequence having the maximum sum
     * ending at the {@link #sequence} index that corresponds with the index of the element in this list. Example:
     * if the contiguous sub-sequence in {@link #sequence} ending at the element before index 5 with the maximum
     * sum starts at index 1, then {@link #startIndexOfMaxSumSequence}{@code .get(5)==1}.
     */
    private List<Integer> startIndexOfMaxSumSequence;
    
    /**
     * Index of the element in {@link #sequence} at which the contiguous sub-sequence ends that has the maximum sum
     * of all such sub-sequences. This means that {@link #maxSumEndingAt}{@code .get(}{@link #endIndexOfMaxSumSequence})
     * is minimal across all elements in {@link #maxSumEndingAt}.
     */
    private int endIndexOfMaxSumSequence;
    
    /**
     * See {@code #maxSumEndingAt}, only for the minimum.
     */
    private final List<ScalableValueWithDistance<ValueType, AveragesTo>> minSumEndingAt;
    
    /**
     * Indices of the first element in {@link #sequence} of the contiguous sub-sequence having the minimum sum
     * ending at the {@link #sequence} index that corresponds with the index of the element in this list. Example:
     * if the contiguous sub-sequence in {@link #sequence} ending at the element before index 5 with the minimum
     * sum starts at index 1, then {@link #startIndexOfMaxSumSequence}{@code .get(5)==1}.
     */
    private List<Integer> startIndexOfMinSumSequence;
    
    /**
     * Index of the element in {@link #sequence} at which the contiguous sub-sequence ends that has the minimum sum
     * of all such sub-sequences. This means that {@link #minSumEndingAt}{@code .get(}{@link #endIndexOfMinSumSequence})
     * is minimal across all elements in {@link #minSumEndingAt}.
     */
    private int endIndexOfMinSumSequence;
    
    public KadaneExtremeSubsequenceFinderLinkedListImpl() {
        sequence = new LinkedList<>();
        maxSumEndingAt = new LinkedList<>();
        minSumEndingAt = new LinkedList<>();
        startIndexOfMaxSumSequence = new LinkedList<>();
        endIndexOfMaxSumSequence = -1;
        startIndexOfMinSumSequence = new LinkedList<>();
        endIndexOfMinSumSequence = -1;
    }

    @Override
    public synchronized void add(int index, T t) {
        final ScalableValueWithDistance<ValueType, AveragesTo> oldMaxSum = getMaxSum();
        final ScalableValueWithDistance<ValueType, AveragesTo> oldMinSum = getMinSum();
        final boolean insertingIntoMaxSumSequence = index <= endIndexOfMaxSumSequence && index > startIndexOfMaxSumSequence.get(endIndexOfMaxSumSequence);
        final boolean insertingIntoMinSumSequence = index <= endIndexOfMinSumSequence && index > startIndexOfMinSumSequence.get(endIndexOfMinSumSequence);
        sequence.add(index, t);
        final ScalableValueWithDistance<ValueType, AveragesTo> newMaxSumEndingAtIndex;
        final ScalableValueWithDistance<ValueType, AveragesTo> sumWithMax = index == 0 ? null : t.add(maxSumEndingAt.get(index-1));
        if (index == 0 || compare(t, sumWithMax) >= 0) {
            newMaxSumEndingAtIndex = t; // one-element sum consisting of element at "index" is the maximum
            startIndexOfMaxSumSequence.add(index, index);
        } else {
            newMaxSumEndingAtIndex = sumWithMax;
            startIndexOfMaxSumSequence.add(index, startIndexOfMaxSumSequence.get(index-1));
        }
        maxSumEndingAt.add(index, newMaxSumEndingAtIndex);
        if (oldMaxSum == null || compare(newMaxSumEndingAtIndex, oldMaxSum) > 0) {
            endIndexOfMaxSumSequence = index;
        }
        final ScalableValueWithDistance<ValueType, AveragesTo> newMinSumEndingAtIndex;
        final ScalableValueWithDistance<ValueType, AveragesTo> sumWithMin = index == 0 ? null : t.add(minSumEndingAt.get(index-1));
        if (index == 0 || compare(t, sumWithMin) <= 0) {
            newMinSumEndingAtIndex = t; // one-element sum consisting of element at "index" is the minimum
            startIndexOfMinSumSequence.add(index, index);
        } else {
            newMinSumEndingAtIndex = sumWithMin;
            startIndexOfMinSumSequence.add(index, startIndexOfMinSumSequence.get(index-1));
        }
        minSumEndingAt.add(index, newMinSumEndingAtIndex);
        if (oldMinSum == null || compare(newMinSumEndingAtIndex, oldMinSum) < 0) {
            endIndexOfMinSumSequence = index;
        }
        if (index < sequence.size()) {
            update(index+1, newMaxSumEndingAtIndex, newMinSumEndingAtIndex);
        }
        if (insertingIntoMaxSumSequence) { // TODO probably also check whether a "positive" value was inserted; in this case, max can only grow further, and sub-sequence indices will stay unchanged
            updateMax();
        }
        if (insertingIntoMinSumSequence) { // TODO probably also check whether a "negative" value was inserted; in this case, min can only shrink further, and sub-sequence indices will stay unchanged
            updateMin();
        }
    }

    private void updateMin() {
        // TODO Auto-generated method stub
        
    }

    private void updateMax() {
        // TODO Auto-generated method stub
        
    }

    private int compare(final ScalableValueWithDistance<ValueType, AveragesTo> a, final ScalableValueWithDistance<ValueType, AveragesTo> b) {
        return a.divide(1).compareTo(b.divide(1));
    }
    
    /**
     * For each element in {@link #sequence} starting at index {@code i}, this method checks whether the {@link #maxSumEndingAt}{@code [i]}
     * still is the maximum of {@link #maxSumEndingAt}{@code [i-1]+sequence[i]} and {@link #sequence}{@code [i]}. If yes, any change to
     * elements with index less than {@code i} do not have to be carried forward any further. Otherwise, {@link #maxSumEndingAt}{@code [i]}
     * is updated, and the process continues at {@code i+1} "recursively" (implemented iteratively, without recursion).
     */
    private void update(int i, ScalableValueWithDistance<ValueType, AveragesTo> maxSumEndingAtPreviousIndex, ScalableValueWithDistance<ValueType, AveragesTo> newMinSumEndingAtIndex) {
        final ListIterator<T> sequenceIter = sequence.listIterator(i);
        final ListIterator<ScalableValueWithDistance<ValueType, AveragesTo>> maxSumEndingAtIter = maxSumEndingAt.listIterator(i);
        final ListIterator<ScalableValueWithDistance<ValueType, AveragesTo>> minSumEndingAtIter = minSumEndingAt.listIterator(i);
        final ListIterator<Integer> startIndexOfMaxSumSequenceIter = startIndexOfMaxSumSequence.listIterator(i);
        final ListIterator<Integer> startIndexOfMinSumSequenceIter = startIndexOfMinSumSequence.listIterator(i);
        boolean finishedMax = false;
        boolean finishedMin = false;
        while (sequenceIter.hasNext() && (!finishedMax || !finishedMin)) {
            final T next = sequenceIter.next();
            if (!finishedMax) {
                final ScalableValueWithDistance<ValueType, AveragesTo> nextMaxSumEndingAt = maxSumEndingAtIter.next();
                startIndexOfMaxSumSequenceIter.next();
                final ScalableValueWithDistance<ValueType, AveragesTo> sum = next.add(maxSumEndingAtPreviousIndex);
                final boolean nextGreaterOrEqualsThanSum = compare(next, sum) >= 0;
                final ScalableValueWithDistance<ValueType, AveragesTo> newMaxSumEndingAt = nextGreaterOrEqualsThanSum ? next : sum;
                if (compare(nextMaxSumEndingAt, newMaxSumEndingAt) != 0) {
                    maxSumEndingAtIter.remove();
                    maxSumEndingAtIter.add(newMaxSumEndingAt);
                    maxSumEndingAtPreviousIndex = newMaxSumEndingAt;
                    startIndexOfMaxSumSequenceIter.remove();
                    startIndexOfMaxSumSequenceIter.add(nextGreaterOrEqualsThanSum ? i : startIndexOfMaxSumSequence.get(i-1));
                    if (compare(newMaxSumEndingAt, getMaxSum()) > 0) { // FIXME getMaxSum() cannot be asked while we are still updating; indices may have moved left (remove) or right (add)!
                        endIndexOfMaxSumSequence = i;
                    }
                } else {
                    finishedMax = true; // no more changes to propagate
                }
            }
            if (!finishedMin) {
                final ScalableValueWithDistance<ValueType, AveragesTo> nextMinSumEndingAt = minSumEndingAtIter.next();
                startIndexOfMinSumSequenceIter.next();
                final ScalableValueWithDistance<ValueType, AveragesTo> sum = next.add(maxSumEndingAtPreviousIndex);
                final boolean nextLessOrEqualsThanSum = compare(next, sum) <= 0;
                final ScalableValueWithDistance<ValueType, AveragesTo> newMinSumEndingAt = nextLessOrEqualsThanSum ? next : sum;
                if (compare(nextMinSumEndingAt, newMinSumEndingAt) != 0) {
                    minSumEndingAtIter.remove();
                    minSumEndingAtIter.add(newMinSumEndingAt);
                    maxSumEndingAtPreviousIndex = newMinSumEndingAt;
                    startIndexOfMinSumSequenceIter.remove();
                    startIndexOfMinSumSequenceIter.add(nextLessOrEqualsThanSum ? i : startIndexOfMinSumSequence.get(i-1));
                    if (compare(newMinSumEndingAt, getMinSum()) < 0) {
                        endIndexOfMinSumSequence = i;
                    }
                } else {
                    finishedMin = true; // no more changes to propagate
                }
            }
            i++;
        }
    }

    @Override
    public synchronized void remove(int index) {
        sequence.remove(index);
        final ScalableValueWithDistance<ValueType, AveragesTo> maxSumEndingAtIndex = maxSumEndingAt.remove(index);
        startIndexOfMaxSumSequence.remove(index);
        if (endIndexOfMaxSumSequence > index) {
            endIndexOfMaxSumSequence--;
        } else if (endIndexOfMaxSumSequence == index) {
            // TODO but if endIndexOfMaxSumSequence == index, we have deleted the last element of the max sum sequence and need to re-evaluate
        }
        if (endIndexOfMinSumSequence > index) {
            endIndexOfMinSumSequence--;
        } else if (endIndexOfMinSumSequence == index) {
            // TODO but if endIndexOfMinSumSequence == index, we have deleted the last element of the min sum sequence and need to re-evaluate
        }
        final ScalableValueWithDistance<ValueType, AveragesTo> minSumEndingAtIndex = minSumEndingAt.remove(index);
        startIndexOfMinSumSequence.remove(index);
        update(index+1, maxSumEndingAtIndex, minSumEndingAtIndex);
    }
    
    @Override
    public synchronized void add(T t) {
        add(sequence.size(), t);
    }
    
    @Override
    public synchronized void remove(T t) {
        remove(sequence.indexOf(t));
    }
    
    @Override
    public ScalableValueWithDistance<ValueType, AveragesTo> getMaxSum() {
        return endIndexOfMaxSumSequence == -1 ? null : maxSumEndingAt.get(endIndexOfMaxSumSequence);
    }
    
    @Override
    public ScalableValueWithDistance<ValueType, AveragesTo> getMinSum() {
        return endIndexOfMinSumSequence == -1 ? null : minSumEndingAt.get(endIndexOfMinSumSequence);
    }
    
    @Override
    public int getStartIndexOfMaxSumSequence() {
        return startIndexOfMaxSumSequence.isEmpty() ? -1 : startIndexOfMaxSumSequence.get(endIndexOfMaxSumSequence);
    }

    /**
     * @return the index into {@link #sequence} holding the last element of the contiguous sub-sequence that has the
     *         maximal sum; note that pointing <em>to</em> and not <em>after</em> the last element of that sequence is
     *         slightly different from how indices may be handled in some other from/to collection operations.
     */
    @Override
    public int getEndIndexOfMaxSumSequence() {
        return endIndexOfMaxSumSequence;
    }

    @Override
    public int getStartIndexOfMinSumSequence() {
        return startIndexOfMinSumSequence.isEmpty() ? -1 : startIndexOfMinSumSequence.get(endIndexOfMinSumSequence);
    }

    /**
     * @return the index into {@link #sequence} holding the last element of the contiguous sub-sequence that has the
     *         minimal sum; note that pointing <em>to</em> and not <em>after</em> the last element of that sequence is
     *         slightly different from how indices may be handled in some other from/to collection operations.
     */
    @Override
    public int getEndIndexOfMinSumSequence() {
        return endIndexOfMinSumSequence;
    }

    @Override
    public Iterator<T> iterator() {
        return sequence.iterator();
    }

    @Override
    public Iterator<T> getSubSequenceWithMaxSum() {
        return sequence.subList(getStartIndexOfMaxSumSequence(), getEndIndexOfMaxSumSequence()+1).iterator();
    }

    @Override
    public Iterator<T> getSubSequenceWithMinSum() {
        return sequence.subList(getStartIndexOfMinSumSequence(), getEndIndexOfMinSumSequence()+1).iterator();
    }

    @Override
    public int size() {
        return sequence.size();
    }
}
