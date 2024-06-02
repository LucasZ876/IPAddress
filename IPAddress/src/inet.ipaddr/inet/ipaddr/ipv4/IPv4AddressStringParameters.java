/*
 * Copyright 2016-2024 Sean C Foley
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

package inet.ipaddr.ipv4;

import inet.ipaddr.Address;
import inet.ipaddr.AddressStringParameters;
import inet.ipaddr.AddressStringParameters.RangeParameters;
import inet.ipaddr.IPAddressStringParameters;
import inet.ipaddr.IPAddressStringParameters.IPAddressStringFormatParameters;
import inet.ipaddr.ipv6.IPv6AddressStringParameters;

/**
 * The IPv4-specific parameters within a {@link IPAddressStringParameters} instance.
 * 
 * @author sfoley
 *
 */
public class IPv4AddressStringParameters extends IPAddressStringFormatParameters implements Comparable<IPv4AddressStringParameters> {
	
	private static final long serialVersionUID = 4L;

	public static final boolean DEFAULT_ALLOW_IPV4_INET_ATON = true;
	public static final boolean DEFAULT_ALLOW_IPV4_INET_ATON_SINGLE_SEGMENT_MASK = false; //When not allowing prefixes beyond address size, whether 1.2.3.4/33 has a mask of ipv4 address 33 rather than treating it like a prefix
	public static final boolean DEFAULT_ALLOW_IPV4_inet_aton_extraneous_digits = false;

	/**
	 * Allows ipv4 inet_aton hexadecimal format 0xa.0xb.0xc.0xd
	 */
	public final boolean inet_aton_hex;
	
	/**
	 * Allows ipv4 inet_aton octal format, 04.05.06.07 being an example.
	 * Can be overridden by {@link IPAddressStringFormatParameters#allowLeadingZeros}
	 */
	public final boolean inet_aton_octal;
	
	/**
	 * Allows ipv4 inet_aton hexadecimal or octal to have leading zeros, such as in the first two segments of 0x0a.00b.c.d
	 * The first 0 is not considered a leading zero, it either denotes octal or hex depending on whether it is followed by an 'x'.
	 * Zeros that appear afterwards are inet_aton leading zeros.
	 */
	public final boolean inet_aton_leading_zeros;
	
	/**
	 * Allows ipv4 joined segments like 1.2.3, 1.2, or just 1
	 * 
	 * For the case of just 1 segment, the behaviour is controlled by {@link AddressStringParameters#allowSingleSegment}
	 */
	public final boolean inet_aton_joinedSegments;
	
	/**
	 * If you allow ipv4 joined segments, whether you allow a mask that looks like a prefix length: 1.2.3.5/255
	 */
	public final boolean inet_aton_single_segment_mask;
	
	/**
	 * Allows single-segment inet_aton strings to have extraneous digits.
	 * This allows up to 31 digits when parsing for both IPv4 and IPv6.
	 * This allows an unlimited number of digits when parsing for just IPv4 (ie {@link IPAddressStringParameters#allowIPv6} is false).
	 * <p>
	 * Digits that go beyond 32 bits are essentially ignored.
	 * The number of digits before exceeding 32 bits depends on the radix.
	 * The value of the most significant digit before exceeding 32 bits depends on the radix.
	 * <p>
	 * The resulting address is the modulus of the address with the 32-bit unsigned int maximum value, 
	 * or equivalently the truncation of the address to 32 bits.
	 */
	public final boolean inet_aton_extraneous_digits;
	
	/**
	 * The network that will be used to construct addresses - both parameters inside the network, and the network's address creator
	 */
	private final IPv4AddressNetwork network;
	
	/**
	 * Retained for backwards compatibility<br>
	 * 
	 * Library users are strongly encourage to use the builder classes instead of this constructor.
	 * 
	 * @deprecated
	 */
	@Deprecated
	public IPv4AddressStringParameters(
			boolean allowLeadingZeros,
			boolean allowCIDRPrefixLeadingZeros,
			boolean allowUnlimitedLeadingZeros,
			RangeParameters rangeOptions,
			boolean allowWildcardedSeparator,
			boolean allowPrefixesBeyondAddressSize,
			boolean inet_aton_hex,
			boolean inet_aton_octal,
			boolean inet_aton_leading_zeros,
			boolean inet_aton_joinedSegments,
			boolean inet_aton_single_segment_mask,
			IPv4AddressNetwork network) {
		this(allowLeadingZeros,
			allowCIDRPrefixLeadingZeros,
			allowUnlimitedLeadingZeros,
			rangeOptions,
			allowWildcardedSeparator,
			allowPrefixesBeyondAddressSize,
			false, /* backwards compatibility to retain legacy behaviour, which did not support binary */
			inet_aton_hex,
			inet_aton_octal,
			inet_aton_leading_zeros,
			inet_aton_joinedSegments,
			inet_aton_single_segment_mask,
			false,
			network);
	}
	
	/**
	 * Constructs the parameters for IPv4-specific string parsing.
	 * <br>
	 * Users are strongly encouraged to use the nested Builder class instead of this constructor.
	 * 
	 * @param allowLeadingZeros
	 * @param allowCIDRPrefixLeadingZeros
	 * @param allowUnlimitedLeadingZeros
	 * @param rangeOptions
	 * @param allowWildcardedSeparator
	 * @param allowPrefixesBeyondAddressSize
	 * @param allowBinary
	 * @param inet_aton_hex
	 * @param inet_aton_octal
	 * @param inet_aton_leading_zeros
	 * @param inet_aton_joinedSegments
	 * @param inet_aton_single_segment_mask
	 * @param network
	 */
	public IPv4AddressStringParameters(
			boolean allowLeadingZeros,
			boolean allowCIDRPrefixLeadingZeros,
			boolean allowUnlimitedLeadingZeros,
			RangeParameters rangeOptions,
			boolean allowWildcardedSeparator,
			boolean allowPrefixesBeyondAddressSize,
			boolean allowBinary,
			boolean inet_aton_hex,
			boolean inet_aton_octal,
			boolean inet_aton_leading_zeros,
			boolean inet_aton_joinedSegments,
			boolean inet_aton_single_segment_mask,
			boolean inet_aton_extraneous_digits,
			IPv4AddressNetwork network) {
		super(allowBinary, allowLeadingZeros, allowCIDRPrefixLeadingZeros, allowUnlimitedLeadingZeros, rangeOptions, allowWildcardedSeparator, allowPrefixesBeyondAddressSize);
		this.inet_aton_hex = inet_aton_hex;
		this.inet_aton_octal = inet_aton_octal;
		this.inet_aton_leading_zeros = inet_aton_leading_zeros;
		this.inet_aton_joinedSegments = inet_aton_joinedSegments;
		this.inet_aton_single_segment_mask = inet_aton_single_segment_mask;
		this.inet_aton_extraneous_digits = inet_aton_extraneous_digits;
		this.network = network;
	}

	public Builder toBuilder() {
		Builder builder = new Builder();
		builder.inet_aton_hex = inet_aton_hex;
		builder.inet_aton_octal = inet_aton_octal;
		builder.inet_aton_joinedSegments = inet_aton_joinedSegments;
		builder.inet_aton_single_segment_mask = inet_aton_single_segment_mask;
		builder.inet_aton_extraneous_digits = inet_aton_extraneous_digits;
		builder.network = network;
		return (Builder) toBuilder(builder);
	}

	public static class Builder extends IPAddressStringFormatParameters.BuilderBase {
		private boolean inet_aton_hex = DEFAULT_ALLOW_IPV4_INET_ATON;
		private boolean inet_aton_octal = DEFAULT_ALLOW_IPV4_INET_ATON;
		private boolean inet_aton_leading_zeros = DEFAULT_ALLOW_IPV4_INET_ATON;
		private boolean inet_aton_joinedSegments = DEFAULT_ALLOW_IPV4_INET_ATON;
		private boolean inet_aton_single_segment_mask = DEFAULT_ALLOW_IPV4_INET_ATON_SINGLE_SEGMENT_MASK;
		private boolean inet_aton_extraneous_digits = DEFAULT_ALLOW_IPV4_inet_aton_extraneous_digits;
		private IPv4AddressNetwork network;

		IPv6AddressStringParameters.Builder mixedParent;

		@Override
		protected void setMixedParent(IPv6AddressStringParameters.Builder parent) {
			mixedParent = parent;
		}

		public IPv6AddressStringParameters.Builder getEmbeddedIPv4AddressParentBuilder() {
			return mixedParent;
		}

		/**
		 * Allows joined segments, resulting in just 2, 3 or 4 segments.  Allows octal or hex segments.
		 * Allows an unlimited number of leading zeros.
		 * To allow just a single segment, use {@link IPAddressStringParameters.Builder#allowSingleSegment(boolean)}
		 * This does not affect whether extraneous digits are allowed, which can be allowed with {@link #inet_aton_extraneous_digits}
		 * @param allow
		 * @return
		 */
		public Builder allow_inet_aton(boolean allow) {
			inet_aton_joinedSegments = inet_aton_octal = inet_aton_hex = allow;
			super.allowUnlimitedLeadingZeros(allow);
			return this;
		}

		@Override
		public Builder allowBinary(boolean allow) {
			super.allowBinary(allow);
			return this;
		}

		/**
		 * @see IPv4AddressStringParameters#inet_aton_hex
		 * @param allow
		 * @return the builder
		 */
		public Builder allow_inet_aton_hex(boolean allow) {
			inet_aton_hex = allow;
			return this;
		}

		/**
		 * @see IPv4AddressStringParameters#inet_aton_octal
		 * @param allow
		 * @return the builder
		 */
		public Builder allow_inet_aton_octal(boolean allow) {
			inet_aton_octal = allow;
			return this;
		}

		/**
		 * @see IPv4AddressStringParameters#inet_aton_leading_zeros
		 * @param allow
		 * @return the builder
		 */
		public Builder allow_inet_aton_leading_zeros(boolean allow) {
			inet_aton_leading_zeros = allow;
			return this;
		}

		/**
		 * @see IPv4AddressStringParameters#inet_aton_joinedSegments
		 * @param allow
		 * @return the builder
		 */
		public Builder allow_inet_aton_joined_segments(boolean allow) {
			inet_aton_joinedSegments = allow;
			return this;
		}

		/**
		 * @see IPv4AddressStringParameters#inet_aton_single_segment_mask
		 * @param allow
		 * @return the builder
		 */
		public Builder allow_inet_aton_single_segment_mask(boolean allow) {
			inet_aton_single_segment_mask = allow;
			return this;
		}

		/**
		 * @see IPv4AddressStringParameters#inet_aton_extraneous_digits
		 * @param allow
		 * @return the builder
		 */
		public Builder allow_inet_aton_extraneous_digits(boolean allow) {
			inet_aton_extraneous_digits = allow;
			return this;
		}

		/**
		 * @see IPv4AddressStringParameters#network
		 * @param network if null, the default network will be used
		 * @return the builder
		 */
		public Builder setNetwork(IPv4AddressNetwork network) {
			this.network = network;
			return this;
		}

		@Override
		public Builder setRangeOptions(RangeParameters rangeOptions) {
			super.setRangeOptions(rangeOptions);
			return this;
		}

		@Override
		public Builder allowPrefixesBeyondAddressSize(boolean allow) {
			super.allowPrefixesBeyondAddressSize(allow);
			return this;
		}

		@Override
		public Builder allowWildcardedSeparator(boolean allow) {
			super.allowWildcardedSeparator(allow);
			return this;
		}

		@Override
		public Builder allowLeadingZeros(boolean allow) {
			super.allowLeadingZeros(allow);
			return this;
		}

		@Override
		public Builder allowPrefixLengthLeadingZeros(boolean allow) {
			super.allowPrefixLengthLeadingZeros(allow);
			return this;
		}

		@Override
		public Builder allowUnlimitedLeadingZeros(boolean allow) {
			super.allowUnlimitedLeadingZeros(allow);
			return this;
		}

		public IPv4AddressStringParameters toParams() {
			return new IPv4AddressStringParameters(
					allowLeadingZeros,
					allowPrefixLengthLeadingZeros,
					allowUnlimitedLeadingZeros,
					rangeOptions, 
					allowWildcardedSeparator,
					allowPrefixesBeyondAddressSize,
					allowBinary,
					inet_aton_hex,
					inet_aton_octal,
					inet_aton_leading_zeros,
					inet_aton_joinedSegments,
					inet_aton_single_segment_mask,
					inet_aton_extraneous_digits,
					network);
		}
	}
	
	@Override
	public IPv4AddressNetwork getNetwork() {
		if(network == null) {
			return Address.defaultIpv4Network();
		}
		return network;
	}
	
	@Override
	public IPv4AddressStringParameters clone() {
		try {
			return (IPv4AddressStringParameters) super.clone();
		} catch (CloneNotSupportedException e) {}
		return null;
	}

	@Override
	public int compareTo(IPv4AddressStringParameters o) {
		int result = super.compareTo(o);
		if(result == 0) {
			result = Boolean.compare(inet_aton_hex, o.inet_aton_hex);
			if(result == 0) {
				result = Boolean.compare(inet_aton_octal, o.inet_aton_octal);
				if(result == 0) {
					result = Boolean.compare(inet_aton_joinedSegments, o.inet_aton_joinedSegments);
					if(result == 0) {
						result = Boolean.compare(inet_aton_leading_zeros, o.inet_aton_leading_zeros);
						if(result == 0) {
							result = Boolean.compare(inet_aton_single_segment_mask, o.inet_aton_single_segment_mask);
							if(result == 0) {
								result = Boolean.compare(inet_aton_extraneous_digits, o.inet_aton_extraneous_digits);
							}
						}
					}
				}
			}
		}
		return result;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o instanceof IPv4AddressStringParameters) {
			if(super.equals(o)) {
				IPv4AddressStringParameters other = (IPv4AddressStringParameters) o;
				return inet_aton_hex == other.inet_aton_hex
						&& inet_aton_octal == other.inet_aton_octal
						&& inet_aton_joinedSegments == other.inet_aton_joinedSegments
						&& inet_aton_leading_zeros == other.inet_aton_leading_zeros
						&& inet_aton_single_segment_mask == other.inet_aton_single_segment_mask
						&& inet_aton_extraneous_digits == other.inet_aton_extraneous_digits;
				}
		}

		return false;
	}
	
	@Override
	public int hashCode() {
		int hash = super.hashCode();//super hash code uses only first 6 bits
		if(inet_aton_hex) {
			hash |= 0x40;//7th bit
		}
		if(inet_aton_octal) {
			hash |= 0x80;//8th bit
		}
		if(inet_aton_joinedSegments) {
			hash |= 0x100;//9th bit
		}
		return hash;
	}
}
