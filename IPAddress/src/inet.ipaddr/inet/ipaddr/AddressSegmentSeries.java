/*
 * Copyright 2016-2018 Sean C Foley
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     or at
 *     https://github.com/seancfoley/IPAddress/blob/master/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package inet.ipaddr;

import java.util.Iterator;
import java.util.stream.Stream;

import inet.ipaddr.format.AddressDivisionSeries;
import inet.ipaddr.format.util.AddressComponentSpliterator;
import inet.ipaddr.format.util.AddressComponentRangeSpliterator;

/**
 * Represents a series of address segments, each of equal byte size, the byte size being a whole number of bytes.
 * 
 * Each segment can potentially range over multiple values, and thus this series of segments can represent many different values as well.
 * 
 * 
 * @author sfoley
 *
 */
public interface AddressSegmentSeries extends AddressDivisionSeries, AddressComponent {
	/**
	 * Returns the number of segments in this series.
	 * @return
	 */
	int getSegmentCount();
	
	/**
	 * Returns the number of bits comprising each segment in this series.  Segments in the same series are equal length.
	 * @return
	 */
	int getBitsPerSegment();
	
	/**
	 * Returns the number of bytes comprising each segment in this series.  Segments in the same series are equal length.
	 * @return
	 */
	int getBytesPerSegment();
	
	/**
	 * Returns the maximum possible segment value for this type of address.
	 * 
	 * Note this is not the maximum value of the range of segment values in this specific address,
	 * this is the maximum value of any segment for this address type, and is usually determined by the number of bits per segment.
	 * 
	 * @return  the maximum possible segment value for a series of the same type
	 */
	int getMaxSegmentValue();

	/**
	 * Gets the subsection from the series that comprises all segments
	 * 
	 * @return
	 */
	AddressSection getSection();

	/**
	 * Gets the subsection from the series starting from the given index
	 * 
	 * The first segment is at index 0.
	 * 
	 * @throws IndexOutOfBoundsException if index is negative
	 * @param index
	 * @return
	 */
	AddressSection getSection(int index);

	/**
	 * Gets the subsection from the series starting from the given index and ending just before the give endIndex
	 * 
	 * The first segment is at index 0.
	 * 
	 * @throws IndexOutOfBoundsException if index is negative or endIndex extends beyond the end of the series
	 * @param index
	 * @param endIndex
	 * @return
	 */
	AddressSection getSection(int index, int endIndex);

	/**
	 * Returns the segment from this series at the given index.
	 * 
	 * The first segment is at index 0.  
	 * A segment is an address division, see {@link AddressDivisionSeries#getDivision(int)}, the difference being that all segments in a given series are the same bit count, while divisions can have variable length.
	 * 
	 * @throws IndexOutOfBoundsException if the index is negative or as large as the segment count
	 * 
	 * @return
	 */
	AddressSegment getSegment(int index);
	
	/**
	 * Returns the an array with the values of each segment as they would appear in the normalized with wildcards string.
	 * 
	 * @return
	 */
	String[] getSegmentStrings();
	
	/**
	 * Copies the existing segments into the given array.  The array size should be at least as large as {@link #getSegmentCount()} 
	 * 
	 * @throws IndexOutOfBoundsException if the provided array is too small
	 */
	void getSegments(AddressSegment segs[]);
	
	/**
	 * get the segments from start to end and insert into the segs array at the given index
	 * @param start the first segment index from this series to be included
	 * @param end the first segment index to be excluded
	 * @param segs the target array
	 * @param index where to insert the segments in the segs array
	 */
	void getSegments(int start, int end, AddressSegment segs[], int index);
	
	/**
	 * Returns the segments of this series of segments as an array.  This must create a new array, so for efficiency use {@link #getSegment(int)} and {@link #getSegmentCount()} instead when feasible.
	 * 
	 * @return
	 */
	AddressSegment[] getSegments();
	
	/**
	 * If this represents a series with ranging values, returns a series representing the lower values of the range.
	 * If this represents an series with a single value in each segment, returns this.
	 * 
	 * @return
	 */
	@Override
	AddressSegmentSeries getLower();
	
	/**
	 * If this represents a series with ranging values, returns a series representing the upper values of the range.
	 * If this represents a series with a single value in each segment, returns this.
	 * 
	 * @return
	 */
	@Override
	AddressSegmentSeries getUpper();
	
	/**
	 * Analogous to {@link java.math.BigInteger#testBit},
	 * Computes (this &amp; (1 &lt;&lt; n)) != 0)
	 * 
	 * @see #isOneBit(int)
	 * @throws IndexOutOfBoundsException if the index is negative or as large as the bit count
	 * 
	 * @param n
	 * @return
	 */
	default boolean testBit(int n) {
		return isOneBit(getBitCount() - (n + 1));
	}
	
	/**
	 * Returns true if the bit in the lower value of this series at the given index is 1, where index 0 is the most significant bit.
	 * 
	 * For example, isOneBit(0) for 128.0.0.0 and isOneBit(31) for 0.0.0.1 are both true, while
	 * isOneBit(31) for 128.0.0.0/24 is false.  isOneBit(0) for 8000:: is true.
	 * 
	 * @see #testBit(int)
	 * @throws IndexOutOfBoundsException if the index is negative or as large as the bit count
	 * 
	 * @param prefixBitIndex
	 * @return
	 */
	default boolean isOneBit(int prefixBitIndex) {
		int bitsPerSegment = getBitsPerSegment();
		AddressSegment segment = getSegment(prefixBitIndex / bitsPerSegment);
		int segmentBitIndex = prefixBitIndex % bitsPerSegment;
		// doing the calculation here allows us to skip bounds checking in AddressSegment#isOneBit
		int value = segment.getSegmentValue();
		return (value & (1 << (bitsPerSegment - (segmentBitIndex + 1)))) != 0;
	}
	
	@Override
	Iterable<? extends AddressSegmentSeries> getIterable();
	
	@Override
	Iterator<? extends AddressSegmentSeries> iterator();

	@Override
	AddressComponentSpliterator<? extends AddressSegmentSeries> spliterator();

	@Override
	Stream<? extends AddressSegmentSeries> stream();

	/**
	 * Iterates through the individual prefixes.
	 * <p>
	 * If the series has no prefix length, then this is equivalent to {@link #iterator()}
	 */
	Iterator<? extends AddressSegmentSeries> prefixIterator();

	/**
	 * Partitions and traverses through the individual prefixes for the prefix length of this series.
	 * 
	 * @return
	 */
	AddressComponentSpliterator<? extends AddressSegmentSeries> prefixSpliterator();

	/**
	 * Iterates through the individual prefix blocks.
	 * <p>
	 * If the series has no prefix length, then this is equivalent to {@link #iterator()}
	 */
	Iterator<? extends AddressSegmentSeries> prefixBlockIterator();

	/**
	 * Returns a sequential stream of the individual prefixes for the prefix length of this series.  For a parallel stream, call {@link Stream#parallel()} on the returned stream.
	 * 
	 * @return
	 */
	Stream<? extends AddressSegmentSeries> prefixStream();

	/**
	 * Partitions and traverses through the individual prefix blocks for the prefix length of this series.
	 * 
	 * @return
	 */
	AddressComponentSpliterator<? extends AddressSegmentSeries> prefixBlockSpliterator();

	/**
	 * Returns a sequential stream of the individual prefix blocks for the prefix length of this series.  For a parallel stream, call {@link Stream#parallel()} on the returned stream.
	 * 
	 * @return
	 */
	Stream<? extends AddressSegmentSeries> prefixBlockStream();
	
	/**
	 * Iterates through the individual segments.
	 */
	Iterator<? extends AddressSegment[]> segmentsIterator();

	/**
	 * Partitions and traverses through the individual segment arrays.
	 * 
	 * @return
	 */
	AddressComponentRangeSpliterator<? extends AddressSegmentSeries, ? extends AddressSegment[]> segmentsSpliterator();

	/**
	 * Returns a sequential stream of the individual segment arrays.  For a parallel stream, call {@link Stream#parallel()} on the returned stream.
	 * 
	 * @return
	 */
	Stream<? extends AddressSegment[]> segmentsStream();

	/**
	 * Returns the series from the subnet that is the given increment upwards into the subnet range, with the increment of 0
	 * returning the first address in the range.
	 * 
	 * <p>
	 * If the subnet has multiple values and the increment exceeds the subnet size, then the 
	 * amount by which it exceeds the size - 1 is added to the upper series of the range (the final iterator value).
	 * <p>
	 * If the increment is negative, it is added to the lower series of the range (the first iterator value).  
	 * <p>
	 * If the subnet is just a single address values, the series is simply incremented by the given value, positive or negative.
	 * <p>
	 * If a subnet has multiple values, a positive increment value is equivalent to the same number of values from the {@link #iterator()}
	 * For instance, a increment of 0 is the first value from the iterator, an increment of 1 is the second value from the iterator, and so on. 
	 * A negative increment added to the subnet count is equivalent to the same number of values preceding the upper bound of the iterator.
	 * For instance, an increment of count - 1 is the last value from the iterator, an increment of count - 2 is the second last value, and so on.
	 * <p>
	 * An increment of size count gives you the series just above the highest series of the subnet.
	 * To get the series just below the lowest series of the subnet, use the increment -1.
	 * 
	 * @param increment
	 * @throws AddressValueException in case of underflow or overflow
	 * @return
	 */
	AddressSegmentSeries increment(long increment) throws AddressValueException;

	/**
	 * If the given increment is positive, adds the value to the upper series ({@link #getUpper()}) in the subnet range to produce a new series.
	 * If the given increment is negative, adds the value to the lower series ({@link #getLower()}) in the subnet range to produce a new series.
	 * If the increment is zero, returns this.
	 * <p>
	 * In the case where the series is a single value, this simply returns the address produced by adding the given increment to this address series.
	 * <p>
	 * 
	 * @param increment
	 * @throws AddressValueException in case of underflow or overflow
	 * @return
	 */
	AddressSegmentSeries incrementBoundary(long increment) throws AddressValueException;

	/**
	 * Produces the canonical representation of the address
	 * @return
	 */
	String toCanonicalString();

	/**
	 * Produces a short representation of the address while remaining within the confines of standard representation(s) of the address
	 * @return
	 */
	String toCompressedString();
	
	/**
	 * Returns a new segment series with the segments reversed.
	 * 
	 * This does not throw {@link IncompatibleAddressException} since all address series can reverse their segments.
	 * 
	 * @return
	 */
	AddressSegmentSeries reverseSegments();
	
	/**
	 * Returns a new segment series with the bits reversed.
	 * 
	 * @throws IncompatibleAddressException if reversing the bits within a single segment cannot be done 
	 * because the segment represents a range, and when all values in that range are reversed, the result is not contiguous.
	 * 
	 * In practice this means that to be reversible the range must include all values except possibly the largest and/or smallest.
	 * 
	 * @return
	 */
	@Override
	AddressSegmentSeries reverseBits(boolean perByte);

	/**
	 * Returns a new segment series with the bytes reversed.
	 * 
	 * @throws IncompatibleAddressException if the segments have more than 1 bytes, 
	 * and if reversing the bits within a single segment cannot be done because the segment represents a range that is not the entire segment range.
	 * 
	 * @return
	 */
	@Override
	AddressSegmentSeries reverseBytes();
	
	/**
	 * Returns a new segment series with the bytes reversed within each segment.
	 * 
	 * @throws IncompatibleAddressException if the segments have more than 1 bytes, 
	 * and if reversing the bits within a single segment cannot be done because the segment represents a range that is not the entire segment range.
	 * 
	 * @return
	 */
	AddressSegmentSeries reverseBytesPerSegment();

	/**
	 * If this series has a prefix length, returns the block for that prefix. Otherwise, this address series is returned.
	 * 
	 * @return the block of address series for the prefix length
	 */
	AddressSegmentSeries toPrefixBlock();

	/**
	 * Removes the prefix length while zeroing out the bits beyond the prefix.
	 * <p>
	 * If the series already has a prefix length, the bits outside the prefix become zero.
	 * Use {@link #withoutPrefixLength()} to remove the prefix length without changing the series values.
	 * <p>
	 * Equivalent to calling removePrefixLength(true)
	 * <p>
	 * @see #withoutPrefixLength() for an alternative which does not change the address series values.
	 * 
	 * @deprecated to remove the prefix length, use {@link #withoutPrefixLength()}, 
	 * 	to remove the prefix length and zero out the bits beyond the prefix, use {@link #adjustPrefixLength(int)}
	 *  with {@link #getBitCount()} as the argument, as in adjustPrefixLength(getBitCount())
	 * 
	 * @return
	 */
	@Deprecated
	AddressSegmentSeries removePrefixLength();

	/**
	 * Provides the same address with no prefix.  The values remain unchanged.
	 * <p>
	 * Use {@link #removePrefixLength()} as an alternative that deletes the host at the same time by zeroing the host values. 
	 */
	AddressSegmentSeries withoutPrefixLength();
	
	/**
	 * Removes the prefix length.   If zeroed is false, the bits that were host bits do not become zero, unlike {@link #removePrefixLength()}
	 * 
	 * @param zeroed whether the bits outside the prefix become zero
	 * @deprecated use {@link #removePrefixLength()} or {@link #withoutPrefixLength()}
	 * @return
	 */
	@Deprecated
	AddressSegmentSeries removePrefixLength(boolean zeroed);

	/**
	 * Increases or decreases prefix length to the next segment boundary.
	 * <p>
	 * Follows the same rules as {@link #adjustPrefixLength(int)}:<br>
	 * When prefix length is increased, the bits moved within the prefix become zero.
	 * When a prefix length is decreased, the bits moved outside the prefix become zero.
	 * To avoid the zeroing behaviour, use {@link #adjustPrefixBySegment(boolean, boolean)} with second arg false.
	 * <p>
	 * @param nextSegment whether to move prefix to previous or following segment boundary
	 * @return
	 */
	AddressSegmentSeries adjustPrefixBySegment(boolean nextSegment);
	
	/**
	 * Increases or decreases prefix length to the next segment boundary.
	 * 
	 * @param nextSegment whether to move prefix to previous or following segment boundary
	 * @param zeroed whether the bits that move from one side of the prefix to the other become zero or retain their original values
	 * @return
	 */
	AddressSegmentSeries adjustPrefixBySegment(boolean nextSegment, boolean zeroed);
	
	/**
	 * Increases or decreases prefix length by the given increment.
	 * <p>
	 * When prefix length is increased, the bits moved within the prefix become zero.
	 * When the prefix is extended beyond the segment series boundary, it is removed.
	 * When a prefix length is decreased, the bits moved outside the prefix become zero.
	 * To avoid the zeroing behaviour, use {@link #adjustPrefixLength(int, boolean)} with second arg false.
	 * 
	 * @param adjustment
	 * @return
	 */
	AddressSegmentSeries adjustPrefixLength(int adjustment);
	
	/**
	 * Increases or decreases prefix length by the given increment.
	 * 
	 * @param zeroed whether the bits that move from one side of the prefix to the other become zero or retain their original values
	 * @param adjustment the increment
	 * @return
	 */
	AddressSegmentSeries adjustPrefixLength(int adjustment, boolean zeroed);
	
	/**
	 * Sets the prefix length.
	 * <p>
	 * If this series has a prefix length, and the prefix length is increased, the bits moved within the prefix become zero.
	 * For an alternative that does not set bits to zero, use {@link #setPrefixLength(int, boolean)} with the second argument as false.
	 * <p>
	 * When the prefix is extended beyond the segment series boundary, it is removed.
	 * <p>
	 * The bits that move from one side of the prefix length to the other (ie bits moved into the prefix or outside the prefix) are zeroed.
	 *
	 * @param prefixLength
	 * @return
	 */
	AddressSegmentSeries setPrefixLength(int prefixLength);

	/**
	 * Sets the prefix length.
	 * <p>
	 * When the prefix is extended beyond the segment series boundary, it is removed.
	 * <p>
	 * @param zeroed whether the bits that move from one side of the prefix length to the other (ie bits moved into the prefix or outside the prefix) are zeroed.
	 * @return
	 */
	AddressSegmentSeries setPrefixLength(int prefixLength, boolean zeroed);
	
	/**
	 * Applies the given prefix length to create a new segment series.
	 * <p>
	 * Similar to {@link #setPrefixLength(int)} except that prefix lengths are never increased. 
	 * When this series already has a prefix length that is less than or equal to the requested prefix length, this series is returned.
	 * <p>
	 * Otherwise the returned series has the given prefix length.
	 * <p>
	 * The bits moved outside the prefix will become zero in the returned series.
	 *
	 * @deprecated use #setPrefixLength(int)
	 * @see #setPrefixLength(int)
	 * @param prefixLength
	 * @return
	 */
	@Deprecated
	AddressSegmentSeries applyPrefixLength(int prefixLength);
}
