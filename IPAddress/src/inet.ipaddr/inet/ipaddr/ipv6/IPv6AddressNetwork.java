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

import java.io.Serializable;
import java.net.Inet6Address;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Function;

import inet.ipaddr.Address.SegmentValueProvider;
import inet.ipaddr.AddressNetwork;
import inet.ipaddr.HostIdentifierString;
import inet.ipaddr.IPAddress.IPVersion;
import inet.ipaddr.IPAddressNetwork;
import inet.ipaddr.IPAddressSection;
import inet.ipaddr.ipv4.IPv4AddressSection;
import inet.ipaddr.ipv6.IPv6Address.IPv6Zone;
import inet.ipaddr.ipv6.IPv6AddressSection.EmbeddedIPv6AddressSection;
import inet.ipaddr.mac.MACAddress;
import inet.ipaddr.mac.MACAddressSection;

/**
 * Provides methods and types associated with all IPv6 addresses.
 * 
 * @author scfoley
 *
 */
public class IPv6AddressNetwork extends IPAddressNetwork<IPv6Address, IPv6AddressSection, IPv4AddressSection, IPv6AddressSegment, Inet6Address> {
	
	private static final long serialVersionUID = 4L;

	private static PrefixConfiguration defaultPrefixConfiguration = AddressNetwork.getDefaultPrefixConfiguration();

	static final IPv6AddressSegment EMPTY_SEGMENTS[] = {};
	private static final IPv6AddressSection EMPTY_SECTION[] = {};
	private static final IPv6Address EMPTY_ADDRESS[] = {};
	
	private static boolean CACHE_SEGMENTS_BY_PREFIX = true;
	
	private IPv6AddressSection linkLocalPrefix;
	
	public static class IPv6AddressCreator extends IPAddressCreator<IPv6Address, IPv6AddressSection, IPv4AddressSection, IPv6AddressSegment, Inet6Address> {
		private static final long serialVersionUID = 4L;
		
		protected static class Cache implements Serializable {
			
			private static final long serialVersionUID = 1L;

			private transient IPv6AddressSegment ZERO_PREFIX_SEGMENT, ALL_RANGE_SEGMENT;

			//there are 0x10000 (ie 0xffff + 1 or 64k) possible segment values in IPv6.  We break the cache into 0x100 blocks of size 0x100
			private transient IPv6AddressSegment segmentCache[][];
		
			//we maintain a similar cache for each potential prefixed segment.  
			//Note that there are 2 to the n possible values for prefix n
			//We break up that number into blocks of size 0x100
			private transient IPv6AddressSegment segmentPrefixCache[][][];
			private transient IPv6AddressSegment allPrefixedCache[];
			
			private static final int MAX_ZONE_ENTRIES = 100;

			@SuppressWarnings("serial")
			private transient LinkedHashMap<String, IPv6Zone> zoneInterfaceCache = new LinkedHashMap<String, IPv6Zone>(16, 0.75f, true) {
				
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, IPv6Zone> eldest) {
					return size() > MAX_ZONE_ENTRIES;
				}
			};
			private ReadWriteLock zoneInterfaceCacheLock = new ReentrantReadWriteLock();
			
			private transient IPv6Zone scopedZoneCache[] = new IPv6Zone[256];
	
			void clear() {
				segmentCache = null;
				allPrefixedCache = null;
				segmentPrefixCache = null;
				ZERO_PREFIX_SEGMENT = null;
				ALL_RANGE_SEGMENT = null;
				IPv6Zone scopedZoneCache2[] = scopedZoneCache;
				scopedZoneCache = new IPv6Zone[256];
				Arrays.fill(scopedZoneCache2, null);
				Lock lock = zoneInterfaceCacheLock.writeLock();
				lock.lock();
				zoneInterfaceCache.clear();
				lock.unlock();
			}
		}
		
		Cache cache;
		boolean useSegmentCache = true;

		public IPv6AddressCreator(IPv6AddressNetwork network) {
			super(network);
			cache = new Cache();
		}

		protected IPv6AddressCreator(IPv6AddressNetwork network, Cache cache) {
			super(network);
			this.cache = cache;
		}

		@Override
		public void clearCaches() {
			super.clearCaches();
			cache.clear();
		}

		@Override
		public void setSegmentCaching(boolean enable) {
			useSegmentCache = enable;
		}

		@Override
		public IPv6AddressNetwork getNetwork() {
			return (IPv6AddressNetwork) super.getNetwork();
		}

		@Override
		public int getMaxValuePerSegment() {
			return IPv6Address.MAX_VALUE_PER_SEGMENT;
		}
		
		@Override
		protected int getAddressSegmentCount() {
			return IPv6Address.SEGMENT_COUNT;
		}

		@Override
		public IPv6AddressSegment[] createSegmentArray(int length) {
			if(length == 0) {
				return EMPTY_SEGMENTS;
			}
			return new IPv6AddressSegment[length];
		}
		
		@Override
		public IPv6AddressSegment createSegment(int value) {
			if(useSegmentCache && value >= 0 && value <= IPv6Address.MAX_VALUE_PER_SEGMENT) {
				IPv6AddressSegment result, block[], cache[][] = this.cache.segmentCache;
				int blockIndex = value >>> 8; // divide by 0x100
				int resultIndex = value - (blockIndex << 8); // mod 0x100
				if(cache == null) {
					this.cache.segmentCache = cache = new IPv6AddressSegment[((2 * IPv6Address.MAX_VALUE_PER_SEGMENT) - 1) / 0x100][];
					cache[blockIndex] = block = new IPv6AddressSegment[0x100];
					result = block[resultIndex] = new IPv6AddressSegment(value);
				} else {
					block = cache[blockIndex];
					if(block == null) {
						cache[blockIndex] = block = new IPv6AddressSegment[0x100];
						result = block[resultIndex] = new IPv6AddressSegment(value);
					} else {
						result = block[resultIndex];
						if(result == null) {
							result = block[resultIndex] = new IPv6AddressSegment(value);
						}
					}
				}
				return result;
			}
			return new IPv6AddressSegment(value);
		}
		
		@Override
		public IPv6AddressSegment createSegment(int value, Integer segmentPrefixLength) {
			if(segmentPrefixLength == null) {
				return createSegment(value);
			}
			if(useSegmentCache && value >= 0 && value <= IPv6Address.MAX_VALUE_PER_SEGMENT && segmentPrefixLength >= 0 && segmentPrefixLength <= IPv6Address.BIT_COUNT) {
				if(segmentPrefixLength == 0 && getNetwork().getPrefixConfiguration().allPrefixedAddressesAreSubnets()) {
					IPv6AddressSegment result = cache.ZERO_PREFIX_SEGMENT;
					if(result == null) {
						cache.ZERO_PREFIX_SEGMENT = result = new IPv6AddressSegment(0, 0);
					}
					return result;
				}
				if(CACHE_SEGMENTS_BY_PREFIX) {
					int prefixIndex = segmentPrefixLength;
					int valueIndex;
					boolean isAllSubnets = getNetwork().getPrefixConfiguration().allPrefixedAddressesAreSubnets();
					if(isAllSubnets) {
						int mask = getNetwork().getSegmentNetworkMask(segmentPrefixLength);
						value &= mask;
						valueIndex = value >>> (IPv6Address.BITS_PER_SEGMENT- segmentPrefixLength);
					} else {
						valueIndex = value;
					}
					IPv6AddressSegment result, block[], prefixCache[][], cache[][][] = this.cache.segmentPrefixCache;
					int blockIndex = valueIndex >>> 8; // divide by 0x100
					int resultIndex = valueIndex - (blockIndex << 8); // mod 0x100
					if(cache == null) {
						this.cache.segmentPrefixCache = cache = new IPv6AddressSegment[IPv6Address.BITS_PER_SEGMENT + 1][][];
						prefixCache = null;
						block = null;
						result = null;
					} else {
						prefixCache = cache[prefixIndex];
						if(prefixCache != null) {
							block = prefixCache[blockIndex];
							if(block != null) {
								result = block[resultIndex];
							} else {
								result = null;
							}
						} else {
							block = null;
							result = null;
						}
					}
					if(prefixCache == null) {
						int prefixCacheSize = isAllSubnets ? 1 << segmentPrefixLength : IPv6Address.MAX_VALUE_PER_SEGMENT + 1;//number of possible values for each segmentPrefix
						cache[prefixIndex] = prefixCache = new IPv6AddressSegment[(prefixCacheSize + 0x100 - 1) >>> 8][];
					}
					if(block == null) {
						int prefixCacheSize = isAllSubnets ? 1 << segmentPrefixLength : IPv6Address.MAX_VALUE_PER_SEGMENT + 1;//number of possible values for each segmentPrefix
						int highestIndex = prefixCacheSize >>> 8; // divide by 0x100
						if(valueIndex >>> 8 == highestIndex) { // final block: only use an array as large as we need
							block = new IPv6AddressSegment[prefixCacheSize - (highestIndex << 8)]; // mod 0x100
						} else { //all other blocks are size 0x100
							block = new IPv6AddressSegment[0x100];
						}
						prefixCache[blockIndex] = block;
					}
					if(result == null) {
						block[resultIndex] = result = new IPv6AddressSegment(value, segmentPrefixLength);
					}
					return result;
				}
			}
			IPv6AddressSegment result = new IPv6AddressSegment(value, segmentPrefixLength);
			return result;
		}
		
		@Override
		public IPv6AddressSegment createSegment(int lower, int upper, Integer segmentPrefixLength) {
			if(segmentPrefixLength == null) {
				if(lower == upper) {
					return createSegment(lower);
				}
				if(useSegmentCache && lower == 0 && upper == IPv6Address.MAX_VALUE_PER_SEGMENT) {
					IPv6AddressSegment result = cache.ALL_RANGE_SEGMENT;
					if(result == null) {
						cache.ALL_RANGE_SEGMENT = result = new IPv6AddressSegment(0, IPv6Address.MAX_VALUE_PER_SEGMENT, null);
					}
					return result;
				}
			} else {
				if(lower == upper) {
					return createSegment(lower, segmentPrefixLength);
				}
				if(useSegmentCache && lower >= 0 && lower <= IPv6Address.MAX_VALUE_PER_SEGMENT && 
					upper >= 0 && upper <= IPv6Address.MAX_VALUE_PER_SEGMENT && 
						segmentPrefixLength >= 0 && segmentPrefixLength <= IPv6Address.BIT_COUNT) {
					if(segmentPrefixLength == 0 && getNetwork().getPrefixConfiguration().allPrefixedAddressesAreSubnets()) {
						return createSegment(0, segmentPrefixLength);
					}
					if(CACHE_SEGMENTS_BY_PREFIX) {
						int bitsPerSegment = IPv6Address.BITS_PER_SEGMENT;
						if(segmentPrefixLength > bitsPerSegment) {
							segmentPrefixLength = bitsPerSegment;
						}
						if(getNetwork().getPrefixConfiguration().allPrefixedAddressesAreSubnets()) {
							int mask = getNetwork().getSegmentNetworkMask(segmentPrefixLength);
							lower &= mask;
							if((upper & mask) == lower) {
								return createSegment(lower, segmentPrefixLength);
							}
							int hostMask = getNetwork().getSegmentHostMask(segmentPrefixLength);
							upper |= hostMask;
						}
						if(lower == 0 && upper == IPv6Address.MAX_VALUE_PER_SEGMENT) {
							//cache */26 type segments
							int prefixIndex = segmentPrefixLength;
							IPv6AddressSegment result, cache[] = this.cache.allPrefixedCache;
							if(cache == null) {
								this.cache.allPrefixedCache = cache = new IPv6AddressSegment[IPv6Address.BITS_PER_SEGMENT + 1];
								cache[prefixIndex] = result = new IPv6AddressSegment(0, IPv6Address.MAX_VALUE_PER_SEGMENT, segmentPrefixLength);
							} else {
								result = cache[prefixIndex];
								if(result == null) {
									cache[prefixIndex] = result = new IPv6AddressSegment(0, IPv6Address.MAX_VALUE_PER_SEGMENT, segmentPrefixLength);
								}
							}
							return result;
						}
					}
				}
			}
			IPv6AddressSegment result = new IPv6AddressSegment(lower, upper, segmentPrefixLength);
			return result;
		}

		@Override
		public IPv6AddressSection createFullSectionInternal(SegmentValueProvider lowerValueProvider, SegmentValueProvider upperValueProvider, Integer prefix) {
			return new IPv6AddressSection(lowerValueProvider, upperValueProvider, IPv6Address.SEGMENT_COUNT, prefix);
		}

		@Override
		protected IPv6AddressSection createSectionInternal(byte[] bytes, int segmentCount, Integer prefix, boolean singleOnly) {
			return new IPv6AddressSection(bytes, segmentCount, prefix, false, singleOnly);
		}

		@Override
		protected IPv6AddressSection createSectionInternal(IPv6AddressSegment segments[]) {
			return new IPv6AddressSection(segments, 0, false);
		}
		
		@Override
		protected IPv6AddressSection createPrefixedSectionInternal(IPv6AddressSegment segments[], Integer prefix, boolean singleOnly) {
			return new IPv6AddressSection(segments, 0, false, prefix, singleOnly);
		}
		
		@Override
		protected IPv6AddressSection createSectionInternal(IPv6AddressSegment segments[], IPv4AddressSection embeddedSection) {
			IPv6AddressSection result = new IPv6AddressSection(segments, 0, false);
			result.embeddedIPv4Section = embeddedSection;
			return result;
		}
		
		@Override
		protected IPv6AddressSection createSectionInternal(IPv6AddressSegment segments[], IPv4AddressSection embeddedSection, Integer prefix) {
			IPv6AddressSection result = new IPv6AddressSection(segments, 0, false, prefix, false);
			result.embeddedIPv4Section = embeddedSection;
			return result;
		}
		
		@Override
		protected IPv6AddressSection createSectionInternal(IPv6AddressSegment segments[], IPv4AddressSection embeddedSection, Integer prefix, boolean singleOnly) {
			IPv6AddressSection result = new IPv6AddressSection(segments, 0, false, prefix, singleOnly);
			result.embeddedIPv4Section = embeddedSection;
			return result;
		}
		
		protected IPv6AddressSection createEmbeddedSectionInternal(IPv6AddressSection encompassingSection, IPv6AddressSegment segments[], int startIndex) {
			return new EmbeddedIPv6AddressSection(encompassingSection, segments, startIndex);
		}
		
		@Override
		protected IPv6AddressSection createEmbeddedSectionInternal(IPAddressSection encompassingSection, IPv6AddressSegment segments[]) {
			return new EmbeddedIPv6AddressSection((IPv6AddressSection) encompassingSection, segments, 0);
		}
		
		protected IPv6AddressSection createSectionInternal(IPv6AddressSegment segments[], int startIndex) {
			return new IPv6AddressSection(segments, startIndex, false);
		}
		
		@Override
		protected IPv6AddressSection createSectionInternal(IPv6AddressSegment[] segments, int startIndex, boolean extended) {
			return new IPv6AddressSection(segments, startIndex, false);
		}
		
		@Override
		protected IPv6AddressSection[] createSectionArray(int length) {
			if(length == 0) {
				return EMPTY_SECTION;
			}
			return new IPv6AddressSection[length];
		}
		
		@Override
		public IPv6AddressSection createSection(byte bytes[], int byteStartIndex, int byteEndIndex, Integer prefix) {
			return new IPv6AddressSection(bytes, byteStartIndex, byteEndIndex, -1, prefix, true, false);
		}
		
		@Override
		protected IPv6AddressSection createSection(byte bytes[], int byteStartIndex, int byteEndIndex, int segmentCount, Integer prefix) {
			return new IPv6AddressSection(bytes, byteStartIndex, byteEndIndex, segmentCount, prefix, true, false);
		}
		
		protected IPv6AddressSection createSection(long highBytes, long lowBytes, int segmentCount, Integer prefix) {
			return new IPv6AddressSection(highBytes, lowBytes, segmentCount, prefix);
		}

		@Override
		public IPv6AddressSection createSection(byte bytes[], Integer prefix) {
			return new IPv6AddressSection(bytes, prefix);
		}
		
		@Override
		public IPv6AddressSection createSection(IPv6AddressSegment segments[]) {
			return new IPv6AddressSection(segments);
		}
		
		@Override
		public IPv6AddressSection createSection(IPv6AddressSegment segments[], Integer networkPrefixLength) {
			return new IPv6AddressSection(segments, networkPrefixLength);
		}
		
		public IPv6AddressSection createSection(MACAddress eui) {
			return new IPv6AddressSection(eui);
		}
		
		public IPv6AddressSection createSection(MACAddressSection eui) {
			return new IPv6AddressSection(eui);
		}
		
		@Override
		protected IPv6Address[] createAddressArray(int length) {
			if(length == 0) {
				return EMPTY_ADDRESS;
			}
			return new IPv6Address[length];
		}
		
		@Override
		protected IPv6Address createAddressInternal(IPv6AddressSegment segments[]) {
			return super.createAddressInternal(segments);
		}

		@Override
		protected IPv6Address createAddressInternal(IPv6AddressSection section, CharSequence zone) {
			if(zone == null || zone.length() == 0) {
				return createAddress(section);
			}
			String zoneStr = zone.toString().trim();
			if(zoneStr.length() == 0) {
				return createAddress(section);
			}
			IPv6Zone zoneObj = getCacheZoneObj(zoneStr);
			return createAddress(section, zoneObj);
		}

		private IPv6Zone getCacheZoneObj(String zoneStr) {
			int scope = IPv6Zone.checkIfScope(zoneStr);
			IPv6Zone zoneObj;
			if(scope >= 0) {
				if(scope < cache.scopedZoneCache.length) {
					zoneObj = cache.scopedZoneCache[scope];
					if(zoneObj == null) {
						zoneObj = new IPv6Zone(scope);
						cache.scopedZoneCache[scope] = zoneObj;
					}
				} else {
					zoneObj = new IPv6Zone(scope);
				}
				zoneObj.zoneStr = zoneStr;
			} else {
				Lock readLock = cache.zoneInterfaceCacheLock.readLock();
				readLock.lock();
				zoneObj = cache.zoneInterfaceCache.get(zoneStr);
				readLock.unlock();
				if(zoneObj == null) {
					IPv6Zone newZoneObj = new IPv6Zone(zoneStr);
					Lock writeLock = cache.zoneInterfaceCacheLock.writeLock();
					writeLock.lock();
					zoneObj = cache.zoneInterfaceCache.get(zoneStr);
					if(zoneObj == null) {
						zoneObj = newZoneObj;
						cache.zoneInterfaceCache.put(zoneStr, zoneObj);
					}
					writeLock.unlock();
				}
			}
			return zoneObj;
		}
		
		public IPv6Address createAddress(IPv6AddressSegment segments[], IPv6Zone zone) {
			if(zone == null) {
				return createAddressInternal(segments);
			}
			return createAddress(createSectionInternal(segments), zone);
		}
		
		public IPv6Address createAddress(IPv6AddressSection section, IPv6Zone zone) {
			if(zone == null) {
				return createAddress(section);
			}
			return new IPv6Address(section, zone);
		}
		
		@Override
		protected IPv6Address createAddressInternal(IPv6AddressSection section, CharSequence zone, HostIdentifierString from, IPv6Address lower, IPv6Address upper) {
			IPv6Address result = createAddressInternal(section, zone, from);
			result.cache(lower, upper);
			return result;
		}
		
		@Override
		public IPv6Address createAddress(IPv6AddressSection section) {
			return new IPv6Address(section);
		}

		@Override
		public IPv6Address createAddress(Inet6Address addr, Integer networkPrefixLength) {
			return new IPv6Address(addr, networkPrefixLength);
		}
		
		@Override
		public IPv6Address createAddress(Inet6Address addr) {
			return new IPv6Address(addr);
		}
	};

	public IPv6AddressNetwork() {
		super(IPv6Address.class);
	}
	
	@Override
	public PrefixConfiguration getPrefixConfiguration() {
		return defaultPrefixConfiguration;
	}

	/**
	 * Sets the default prefix configuration used by this network.
	 * 
	 * @see #getDefaultPrefixConfiguration()
	 * @see #getPrefixConfiguration()
	 * @see PrefixConfiguration
	 */
	public static void setDefaultPrefixConfiguration(PrefixConfiguration config) {
		defaultPrefixConfiguration = config;
	}
	
	/**
	 * Gets the default prefix configuration used by this network type and version.
	 * 
	 * @see AddressNetwork#getDefaultPrefixConfiguration()
	 * @see PrefixConfiguration
	 */
	public static PrefixConfiguration getDefaultPrefixConfiguration() {
		return defaultPrefixConfiguration;
	}
	
	protected boolean isCompatible(IPv6AddressNetwork other) {
		return super.isCompatible(other);
	}
	
	@Override
	protected BiFunction<IPv6Address, Integer, IPv6AddressSegment> getSegmentProducer() {
		return (address, index) -> address.getSegment(index);
	}
	
	@Override
	protected Function<IPv6Address, IPv6AddressSection> getSectionProducer() {
		return IPv6Address::getSection;
	}
	
	@Override
	protected IPv6AddressCreator createAddressCreator() {
		return new IPv6AddressCreator(this);
	}
	
	@Override
	protected IPv6Address createLoopback() {
		IPv6AddressCreator creator = getAddressCreator();
		IPv6AddressSegment zero = creator.createSegment(0);
		IPv6AddressSegment segs[] = creator.createSegmentArray(IPv6Address.SEGMENT_COUNT);
		segs[0] = segs[1] = segs[2] = segs[3] = segs[4] = segs[5] = segs[6] = zero;
		segs[7] = creator.createSegment(1);
		return creator.createAddressInternal(segs); /* address creation */
	}
	
	public IPv6AddressSection getLinkLocalPrefix() {
		if(linkLocalPrefix == null) {
			synchronized(this) {
				if(linkLocalPrefix == null) {
					linkLocalPrefix = createLinkLocalPrefix();
				}
			}
		}
		return linkLocalPrefix;
	}
	
	private IPv6AddressSection createLinkLocalPrefix() {
		IPv6AddressCreator creator = getAddressCreator();
		IPv6AddressSegment zeroSeg = creator.createSegment(0);
		IPv6AddressSection linkLocalPrefix = creator.createSectionInternal(new IPv6AddressSegment[] {
				creator.createSegment(0xfe80),
				zeroSeg,
				zeroSeg,
				zeroSeg});
		return linkLocalPrefix;
	}
	
	@Override
	public IPv6AddressCreator getAddressCreator() {
		return (IPv6AddressCreator) super.getAddressCreator();
	}
	
	@Override
	public boolean isIPv6() {
		return true;
	}
	
	@Override
	public IPVersion getIPVersion() {
		return IPVersion.IPV6;
	}
}
