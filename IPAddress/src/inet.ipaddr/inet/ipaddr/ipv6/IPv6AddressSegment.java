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

package inet.ipaddr.ipv6;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import inet.ipaddr.Address;
import inet.ipaddr.AddressNetwork.AddressSegmentCreator;
import inet.ipaddr.AddressSegment;
import inet.ipaddr.AddressValueException;
import inet.ipaddr.IPAddress.IPVersion;
import inet.ipaddr.IPAddressSegment;
import inet.ipaddr.IncompatibleAddressException;
import inet.ipaddr.PrefixLenException;
import inet.ipaddr.format.AddressDivisionBase;
import inet.ipaddr.format.util.AddressComponentSpliterator;
import inet.ipaddr.ipv6.IPv6AddressNetwork.IPv6AddressCreator;

/**
 * This represents a segment of an IPv6 address.  For IPv4, segments are 1 byte.  For IPv6, they are two bytes.
 * 
 * Like String and Integer and various others basic objects, segments are immutable, which also makes them thread-safe.
 * 
 * @author sfoley
 *
 */
public class IPv6AddressSegment extends IPAddressSegment implements Iterable<IPv6AddressSegment> {
	
	private static final long serialVersionUID = 4L;

	public static final int MAX_CHARS = 4;
	public static final int BITS_PER_CHAR = 4;

	/**
	 * Constructs a segment of an IPv6 address with the given value.
	 * 
	 * @throws AddressValueException if value is negative or too large
	 * @param value the value of the segment
	 */
	public IPv6AddressSegment(int value) throws AddressValueException {
		super(value);
		if(value > IPv6Address.MAX_VALUE_PER_SEGMENT) {
			throw new AddressValueException(value);
		}
	}
	
	/**
	 * Constructs a segment of an IPv6 address.
	 * 
	 * @throws AddressValueException if value or prefix length is negative or too large
	 * @param value the value of the segment.  If the segmentPrefixLength is non-null, the network prefix of the value is used, and the segment represents all segment values with the same network prefix.
	 * @param segmentPrefixLength the segment prefix length, which can be null
	 */
	public IPv6AddressSegment(int value, Integer segmentPrefixLength) throws AddressValueException {
		super(value, segmentPrefixLength);
		if(value > IPv6Address.MAX_VALUE_PER_SEGMENT) {
			throw new AddressValueException(value);
		}
		if(segmentPrefixLength != null && segmentPrefixLength > IPv6Address.BIT_COUNT) {
			throw new PrefixLenException(segmentPrefixLength);
		}
	}
	
	/**
	 * Constructs a segment of an IPv6 address with the given range of values.
	 * 
	 * @throws AddressValueException if value or prefix length is negative or too large
	 * @param segmentPrefixLength the segment prefix length, which can be null.    If segmentPrefixLength is non-null, this segment represents a range of segment values with the given network prefix length.
	 * @param lower the lower value of the range of values represented by the segment.  If segmentPrefixLength is non-null, the lower value becomes the smallest value with the same network prefix.
	 * @param upper the upper value of the range of values represented by the segment.  If segmentPrefixLength is non-null, the upper value becomes the largest value with the same network prefix.
	 */
	public IPv6AddressSegment(int lower, int upper, Integer segmentPrefixLength) throws AddressValueException {
		super(lower, upper, segmentPrefixLength);
		if(getUpperSegmentValue() > IPv6Address.MAX_VALUE_PER_SEGMENT) {
			throw new AddressValueException(getUpperSegmentValue());
		}
		if(segmentPrefixLength != null && segmentPrefixLength > IPv6Address.BIT_COUNT) {
			throw new PrefixLenException(segmentPrefixLength);
		}
	}
	
	@Override
	public long getMaxValue() {
		return IPv6Address.MAX_VALUE_PER_SEGMENT;
	}

	@Override
	public boolean isIPv6() {
		return true;
	}

	@Override
	public IPVersion getIPVersion() {
		return IPVersion.IPV6;
	}
	
	@Override
	protected byte[] getBytesImpl(boolean low) {
		int val = low ? getSegmentValue() : getUpperSegmentValue();
		return new byte[] {(byte) (val >>> 8), (byte) (0xff & val)};
	}
	
	@Override
	public IPv6AddressNetwork getNetwork() {
		return Address.defaultIpv6Network();
	}
	
	@Override
	protected int getSegmentNetworkMask(int bits) {
		return getNetwork().getSegmentNetworkMask(bits);
	}
	
	@Override
	protected int getSegmentHostMask(int bits) {
		return getNetwork().getSegmentHostMask(bits);
	}
	
	@Override
	public int getMaxSegmentValue() {
		return getMaxSegmentValue(IPVersion.IPV6);
	}
	
	protected IPv6AddressSegment toPrefixNormalizedSeg() {
		return getSegmentCreator().createSegment(getSegmentValue(), getUpperSegmentValue(), IPv6AddressSection.cacheBits(getBitCount()));
	}
	
	protected IPv6AddressSegment toPrefixedSegment(Integer segmentPrefixLength) {
		if(isChangedByPrefix(segmentPrefixLength, getNetwork().getPrefixConfiguration().allPrefixedAddressesAreSubnets())) {
			return super.toPrefixedSegment(segmentPrefixLength, getSegmentCreator());
		}
		return this;
	}
	
	@Override
	public IPv6AddressSegment toNetworkSegment(Integer segmentPrefixLength) {
		return toNetworkSegment(segmentPrefixLength, true);
	}
	
	@Override
	public IPv6AddressSegment toNetworkSegment(Integer segmentPrefixLength, boolean withPrefixLength) {
		if(isNetworkChangedByPrefix(segmentPrefixLength, withPrefixLength)) {
			return super.toNetworkSegment(segmentPrefixLength, withPrefixLength, getSegmentCreator());
		}
		return this;
	}
	
	@Override
	public IPv6AddressSegment toHostSegment(Integer bits) {
		if(isHostChangedByPrefix(bits)) {
			return super.toHostSegment(bits, getSegmentCreator());
		}
		return this;
	}

	@Override
	public IPv6AddressSegment getLower() {
		return getLowestOrHighest(this, getSegmentCreator(), true);
	}

	@Override
	public IPv6AddressSegment getUpper() {
		return getLowestOrHighest(this, getSegmentCreator(), false);
	}

	@Override
	public IPv6AddressSegment reverseBits(boolean perByte) {
		if(isMultiple()) {
			if(perByte ? !isReversibleRangePerByte(this) : !isReversibleRange(this)) { // the per-byte case is new
				throw new IncompatibleAddressException(this, "ipaddress.error.reverseRange");
			}
			if(isPrefixed()) {
				AddressSegmentCreator<IPv6AddressSegment> creator = getSegmentCreator();
				return creator.createSegment(getSegmentValue(), getUpperSegmentValue(), null);
			}
			return this;
		}
		AddressSegmentCreator<IPv6AddressSegment> creator = getSegmentCreator();
		int oldVal = getSegmentValue();
		int newVal = reverseBits((short) oldVal);
		if(perByte) {
			newVal = ((newVal & 0xff) << 8) | (newVal >>> 8);
		}
		if(oldVal == newVal && !isPrefixed()) {
			return this;
		}
		return creator.createSegment(newVal);
	}
	
	@Override
	public IPv6AddressSegment reverseBytes() {
		if(isMultiple()) {
			if(isReversibleRange(this)) {
				//reversible ranges end up being the same as the original
				if(isPrefixed()) {
					AddressSegmentCreator<IPv6AddressSegment> creator = getSegmentCreator();
					return creator.createSegment(getSegmentValue(), getUpperSegmentValue(), null);
				}
				return this;
			}
			throw new IncompatibleAddressException(this, "ipaddress.error.reverseRange");
		}
		AddressSegmentCreator<IPv6AddressSegment> creator = getSegmentCreator();
		int value = getSegmentValue();
		int newValue = ((value & 0xff) << 8) | (value >>> 8);
		if(value == newValue && !isPrefixed()) {
			return this;
		}
		return creator.createSegment(newValue);
	}
	
	@Override
	public IPv6AddressSegment toZeroHost() {
		return toZeroHost(this, getSegmentCreator());
	}
	
	@Override @Deprecated
	public IPv6AddressSegment removePrefixLength(boolean zeroed) {
		return removePrefix(this, zeroed, getSegmentCreator());
	}
	
	@Override @Deprecated
	public IPv6AddressSegment removePrefixLength() {
		return removePrefixLength(true);
	}
	
	@Override
	public IPv6AddressSegment withoutPrefixLength() {
		return removePrefix(this, false, getSegmentCreator());
	}

	protected IPv6AddressCreator getSegmentCreator() {
		return getNetwork().getAddressCreator();
	}

	@Override
	public Iterable<IPv6AddressSegment> getIterable() {
		return this;
	}
	
	Iterator<IPv6AddressSegment> iterator(boolean withPrefix) {
		IPv6AddressSegment original;
		if(!withPrefix && isPrefixed() && !isMultiple()) {
			original = withoutPrefixLength();
		} else {
			original = this;
		}
		return iterator(original, getSegmentCreator(), withPrefix ? getSegmentPrefixLength() : null, false, false);
	}
	
	@Override
	public Iterator<IPv6AddressSegment> iterator() {
		return iterator(!getNetwork().getPrefixConfiguration().allPrefixedAddressesAreSubnets());
	}
	
	@Override
	public AddressComponentSpliterator<IPv6AddressSegment> spliterator() {
		IPv6AddressCreator creator = getSegmentCreator();
		boolean isAllSubnets = getNetwork().getPrefixConfiguration().allPrefixedAddressesAreSubnets();
		Integer segPrefLength = isAllSubnets ? null : getSegmentPrefixLength();
		int bitCount = getBitCount();
		return createSegmentSpliterator(
				this,
				getSegmentValue(),
				getUpperSegmentValue(),
				this::iterator,
				(isLowest, isHighest, value, upperValue) -> iterator(null, value, upperValue, bitCount, creator, segPrefLength, false, false),
				(value, upperValue) -> creator.createSegment(value, upperValue, segPrefLength));
	}
	
	@Override
	public Stream<IPv6AddressSegment> stream() {
		return StreamSupport.stream(spliterator(), false);
	}

	@Override
	public Iterator<IPv6AddressSegment> prefixBlockIterator() {
		return iterator(this, getSegmentCreator(), getSegmentPrefixLength(), true, true);
	}
	
	@Override
	public AddressComponentSpliterator<IPv6AddressSegment> prefixBlockSpliterator() {
		Integer segPrefLength = getSegmentPrefixLength();
		if(segPrefLength == null) {
			return spliterator();
		}
		return prefixBlockSpliterator(segPrefLength);
	}
	
	@Override
	public Stream<IPv6AddressSegment> prefixBlockStream() {
		return StreamSupport.stream(prefixBlockSpliterator(), false);
	}

	Iterator<IPv6AddressSegment> identityIterator() {
		return identityIterator(this);
	}
	
	@Override
	public Iterator<IPv6AddressSegment> prefixBlockIterator(int prefixLength) {
		if(prefixLength < 0) {
			throw new PrefixLenException(prefixLength);
		}
		return iterator(this, getSegmentCreator(), IPv6AddressSection.cacheBits(prefixLength), true, true);
	}
	
	@Override
	public AddressComponentSpliterator<IPv6AddressSegment> prefixBlockSpliterator(int segPrefLength) {
		return prefixBlockSpliterator(
				this,
				segPrefLength,
				getSegmentCreator(),
				this::prefixBlockIterator);
	}
	
	@Override
	public Stream<IPv6AddressSegment> prefixBlockStream(int segPrefLength) {
		return StreamSupport.stream(prefixBlockSpliterator(segPrefLength), false);
	}

	@Override
	public Iterator<IPv6AddressSegment> prefixIterator() {
		return iterator(this, getSegmentCreator(), getSegmentPrefixLength(), true, false);
	}
	
	@Override
	public AddressComponentSpliterator<IPv6AddressSegment> prefixSpliterator() {
		Integer segPrefLength = getSegmentPrefixLength();
		if(segPrefLength == null) {
			return spliterator();
		}
		return prefixSpliterator(
				this,
				segPrefLength,
				getSegmentCreator(),
				this::prefixIterator);
	}
	
	@Override
	public Stream<IPv6AddressSegment> prefixStream() {
		return StreamSupport.stream(prefixSpliterator(), false);
	}

	@Override
	public int getBitCount() {
		return IPv6Address.BITS_PER_SEGMENT;
	}
	
	@Override
	public int getByteCount() {
		return IPv6Address.BYTES_PER_SEGMENT;
	}
	
	@Override
	public int getDefaultTextualRadix() {
		return IPv6Address.DEFAULT_TEXTUAL_RADIX;
	}
	
	@Override
	public int getMaxDigitCount() {
		return MAX_CHARS;
	}
	
	/**
	 * Converts this IPv6 address segment into smaller segments,
	 * copying them into the given array starting at the given index.
	 * 
	 * If a segment does not fit into the array because the segment index in the array is out of bounds of the array,
	 * then it is not copied.
	 * 
	 * @param segs
	 * @param index
	 */
	public <S extends AddressSegment> void getSplitSegments(S segs[], int index, AddressSegmentCreator<S> creator) {
		if(!isMultiple()) {
			int bitSizeSplit = IPv6Address.BITS_PER_SEGMENT >>> 1;
			Integer myPrefix = getSegmentPrefixLength();
			Integer highPrefixBits = getSplitSegmentPrefix(bitSizeSplit, myPrefix, 0);
			Integer lowPrefixBits = getSplitSegmentPrefix(bitSizeSplit, myPrefix, 1);
			if(index >= 0 && index < segs.length) {
				segs[index] = creator.createSegment(highByte(), highPrefixBits);
			}
			if(++index >= 0 && index < segs.length) {
				segs[index] = creator.createSegment(lowByte(), lowPrefixBits);
			}
		} else {
			getSplitSegmentsMultiple(segs, index, creator);
		}
	}
	
	private <S extends AddressSegment> void getSplitSegmentsMultiple(S segs[], int index, AddressSegmentCreator<S> creator) {
		Integer myPrefix = getSegmentPrefixLength();
		int bitSizeSplit = IPv6Address.BITS_PER_SEGMENT >>> 1;
		int val = getSegmentValue();
		int upperVal = getUpperSegmentValue();
		int highLower = highByte(val);
		int highUpper = highByte(upperVal);
		int lowLower = lowByte(val);
		int lowUpper = lowByte(upperVal);
		boolean highIsMult = highLower != highUpper;
		if(highIsMult) {
			// low values must be full range to be able to split this
			if(lowLower != 0 || lowUpper != ~(~0 << bitSizeSplit)) {
				throw new IncompatibleAddressException(this, "ipaddress.error.splitSeg");
			}
		}
		if(index >= 0 && index < segs.length) {
			Integer highPrefixBits = getSplitSegmentPrefix(bitSizeSplit, myPrefix, 0);
			if(highIsMult) {
				segs[index] = creator.createSegment(highLower, highUpper, highPrefixBits);
			} else {
				segs[index] = creator.createSegment(highLower, highPrefixBits);
			}
		}
		if(++index >= 0 && index < segs.length) {
			Integer lowPrefixBits = getSplitSegmentPrefix(bitSizeSplit, myPrefix, 1);
			if(lowLower == lowUpper) {
				segs[index] = creator.createSegment(lowLower, lowPrefixBits);
			} else {
				segs[index] = creator.createSegment(lowLower, lowUpper, lowPrefixBits);
			}
		}
	}
	
	@Override
	public boolean prefixContains(IPAddressSegment other, int segmentPrefixLength) {
		return this == other || (super.prefixContains(other, segmentPrefixLength) && other instanceof IPv6AddressSegment);
	}

	@Override
	public boolean prefixEquals(AddressSegment other, int segmentPrefixLength) {
		return this == other || (super.prefixEquals(other, segmentPrefixLength) && other instanceof IPv6AddressSegment);
	}

	@Override
	public boolean overlaps(AddressSegment other) {
		return this == other || (overlapsSeg(other) && other instanceof IPv6AddressSegment);
	}

	@Override
	public boolean contains(AddressSegment other) {
		return this == other || (containsSeg(other) && other instanceof IPv6AddressSegment);
	}
	
	@Override
	public boolean equals(Object other) {
		return this == other || (other instanceof IPv6AddressSegment && ((IPv6AddressSegment) other).isSameValues((AddressSegment) this));
	}
	
	@Override
	protected boolean isSameValues(AddressDivisionBase other) {
		return other instanceof IPv6AddressSegment && isSameValues((AddressSegment) other);
	}
	
	@Override
	protected int getRangeDigitCountImpl() {
		int prefix = getMinPrefixLengthForBlock();
		int bitCount = getBitCount();
		if(prefix < bitCount && containsSinglePrefixBlock(prefix)) {
			int bitsPerCharacter = IPv6Address.BITS_PER_SEGMENT / MAX_CHARS;
			if(prefix % bitsPerCharacter == 0) {
				return (bitCount - prefix) / bitsPerCharacter;
			}
		}
		return 0;
	}
	
	protected static int toUnsignedStringLength(int value, int radix) {
		return IPAddressSegment.toUnsignedStringLength(value, radix);
	}
	
	protected static StringBuilder toUnsignedString(int value, int radix, StringBuilder appendable) {
		return IPAddressSegment.toUnsignedString(value, radix, appendable);
	}
}
