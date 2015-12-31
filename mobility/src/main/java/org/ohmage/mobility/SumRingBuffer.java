package org.ohmage.mobility;

/**
 * Created by changun on 12/28/15.
 */

/**
 * Ring buffer (fixed size queue) implementation using a circular array (array
 * with wrap-around) that keep track of the sum of the element in it.
 * This is used to implement Windowed Simple Moving Average.
 */
// suppress unchecked warnings in Java 1.5.0_6 and later
@SuppressWarnings("unchecked")
public abstract class SumRingBuffer<T> {
    private T[] buffer;          // queue elements
    private int count = 0;          // number of elements on queue
    private double sum = 0.0;
    private int indexOut = 0;       // index of first element of queue
    private int indexIn = 0;       // index of next available slot

    public SumRingBuffer(int capacity) {
        buffer = (T[]) new Object[capacity];
    }

    // cast needed since no generic array creation in Java
    abstract public double addToSum(T item, double sum);

    abstract public double subtractFromSum(T item, double sum);

    @Override
    public String toString() {
        return "SumRingBuffer{" +
                ", count=" + count +
                ", sum=" + sum +
                ", indexOut=" + indexOut +
                ", indexIn=" + indexIn +
                '}';
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public int size() {
        return count;
    }

    public void push(T item) {
        if (count == buffer.length) {
            throw new RuntimeException("Ring buffer overflow");
        }
        buffer[indexIn] = item;
        indexIn = (indexIn + 1) % buffer.length;     // wrap-around
        count++;
        sum = addToSum(item, sum);
    }

    public T pop() {
        if (isEmpty()) {
            throw new RuntimeException("Ring buffer underflow");
        }
        T item = buffer[indexOut];
        buffer[indexOut] = null;                  // to help with garbage collection
        count--;
        indexOut = (indexOut + 1) % buffer.length; // wrap-around
        sum = subtractFromSum(item, sum);
        return item;
    }

    public T peek() {
        if (isEmpty()) {
            throw new RuntimeException("Ring buffer underflow");
        }
        T item = buffer[indexOut];
        return item;
    }

    public T head() {
        if (isEmpty()) {
            throw new RuntimeException("Ring buffer underflow");
        }
        T item = buffer[indexIn == 0 ? buffer.length - 1 : indexIn - 1];
        return item;
    }

    public double getSum() {
        return sum;
    }


}