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

package inet.ipaddr.format.standard;

import inet.ipaddr.AddressValueException;
import inet.ipaddr.format.AddressDivisionBase;

/**
 * An address division for mac
 * 
 * @author sfoley
 *
 */
public class AddressBitsDivision extends AddressDivision {

	private static final long serialVersionUID = 4L;

	protected final int value; //the lower value
	protected final int upperValue; //the upper value of a range, if not a range it is the same as value
	private final int bitCount;
	private final int defaultRadix;
	
	/**
	 * Constructs a division with the given value, the given number of bits (which must be less than 32), and the given radix for printing the values.
	 * 
	 * @param value
	 * @param bitCount
	 * @param defaultRadix
	 */
	public AddressBitsDivision(int value, int bitCount, int defaultRadix) {
		this(value, value, bitCount, defaultRadix);
	}

	/**
	 * Constructs a division with the given value, the given number of bits (which must be less than 32), and the given radix for printing the values.
	 * 
	 * @param lower
	 * @param upper
	 * @param bitCount
	 * @param defaultRadix
	 */
	public AddressBitsDivision(int lower, int upper, int bitCount, int defaultRadix) {
		if(lower > upper) {
			int tmp = lower;
			lower = upper;
			upper = tmp;
		}
		if(lower < 0) {
			throw new AddressValueException(lower);
		} else if(bitCount < 0 || bitCount >= Integer.SIZE || defaultRadix < MIN_RADIX || defaultRadix > MAX_RADIX) {
			throw new IllegalArgumentException();
		}
		this.bitCount = bitCount;
		if(upper > getMaxValue()) {
			throw new AddressValueException(upper);
		}
		this.value = lower;
		this.upperValue = upper;
		this.defaultRadix = defaultRadix;
	}

	@Override
	public long getDivisionValue() {
		return value;
	}

	@Override
	public long getUpperDivisionValue() {
		return upperValue;
	}
	
	@Override
	protected byte[] getBytesImpl(boolean low) {
		return low ? new byte[] {
						(byte) (value >>> 24),
						(byte) (value >>> 16),
						(byte) (value >>> 8),
						(byte) value} : 
					new byte[] {
						(byte) (value >>> 24),
						(byte) (value >>> 16),
						(byte) (upperValue >>> 8),
						(byte) upperValue};
	}

	@Override
	public int getBitCount() {
		return bitCount;
	}

	@Override
	public int getMaxDigitCount() {
		return getMaxDigitCount(getDefaultTextualRadix(), getBitCount(), getMaxValue());
	}

	@Override
	protected boolean isSameValues(AddressDivisionBase other) {
		if(other instanceof AddressBitsDivision) {
			return isSameValues((AddressBitsDivision) other);
		}
		return false;
	}
	
	protected boolean isSameValues(AddressBitsDivision otherSegment) {
		//note that it is the range of values that matters, the prefix bits do not
		return  value == otherSegment.value && upperValue == otherSegment.upperValue;
	}
	
	@Override
	public boolean equals(Object other) {
		if(other == this) {
			return true;
		}
		if(other instanceof AddressBitsDivision) {
			AddressBitsDivision otherSegments = (AddressBitsDivision) other;
			return getBitCount() == otherSegments.getBitCount() && otherSegments.isSameValues(this);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (int) (value | (upperValue << getBitCount()));
	}

	@Override
	public int getDefaultTextualRadix() {
		return defaultRadix;
	}
}
