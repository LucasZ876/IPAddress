/*
 * Copyright 2016-2022 Sean C Foley
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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.function.Function;

import inet.ipaddr.IPAddress.IPVersion;
import inet.ipaddr.format.validate.HostIdentifierStringValidator;
import inet.ipaddr.format.validate.ParsedHost;
import inet.ipaddr.format.validate.ParsedHostIdentifierStringQualifier;
import inet.ipaddr.format.validate.Validator;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv4.IPv4AddressNetwork.IPv4AddressCreator;
import inet.ipaddr.ipv6.IPv6Address;
import inet.ipaddr.ipv6.IPv6AddressNetwork.IPv6AddressCreator;

/**
 * An internet host name.  Can be a fully qualified domain name, a simple host name, or an ip address string.
 * Can also include a port number or service name (which maps to a port).
 * Can include a prefix length or mask for either an ipaddress or host name string.  An IPv6 address can have an IPv6 zone.
 * <p>
 * <h2>Supported formats</h2>
 * You can use all host or address formats supported by nmap and all address formats supported by {@link IPAddressString}.
 * All manners of domain names are supported. When adding a prefix length or mask to a host name string, it is to denote the subnet of the resolved address.
 * <p>
 * Validation is done separately from DNS resolution to avoid unnecessary DNS lookups.
 * <p>
 * See rfc 3513, 2181, 952, 1035, 1034, 1123, 5890 or the list of rfcs for IPAddress.  For IPv6 addresses in host, see rfc 2732 specifying [] notation
 * and 3986 and 4038 (combining IPv6 [] with prefix or zone) and SMTP rfc 2821 for alternative uses of [] for both IPv4 and IPv6
 * <p>
 * 
 * @custom.core
 * @author sfoley
 */
public class HostName implements HostIdentifierString, Comparable<HostName> {

	private static final long serialVersionUID = 4L;
	private static  IPAddress EMPTY_ADDRS[] = new IPAddress[0];
	
	public static final char LABEL_SEPARATOR = '.';
	public static final char IPV6_START_BRACKET = '[', IPV6_END_BRACKET = ']';
	public static final char PORT_SEPARATOR = ':';
	
	/* Generally permissive, settings are the default constants in HostNameParameters */
	public static final HostNameParameters DEFAULT_VALIDATION_OPTIONS = new HostNameParameters.Builder().toParams();

	private static final HostNameParameters DEFAULT_SOCKET_ADDR_VALIDATION_OPTIONS = DEFAULT_VALIDATION_OPTIONS.toBuilder().expectPort(true).toParams();

	/* the original host in string format */
	private final String host;
	
	/* normalized strings representing the host */
	private transient String normalizedString, normalizedWildcardString;

	/* the host broken into its parsed components */
	private ParsedHost parsedHost;

	private HostNameException validationException;

	/* The address if this host represents an ip address, or the addresses obtained when this host is resolved. */
	IPAddress resolvedAddresses[];
	
	/* validation options */
	final HostNameParameters validationOptions;

	/**
	 * Constructs a host name from an IP address.
	 * 
	 * @param addr
	 */
	public HostName(IPAddress addr) {
		host = addr.toNormalizedString();
		parsedHost = new ParsedHost(host, addr.getProvider());
		validationOptions = null;
	}
	
	/**
	 * Constructs a host name from an IP address and a port.
	 * 
	 * @param addr
	 */
	public HostName(IPAddress addr, int port) {
		ParsedHostIdentifierStringQualifier qualifier = new ParsedHostIdentifierStringQualifier(null, port);
		host = toNormalizedString(addr, port);
		parsedHost = new ParsedHost(host, addr.getProvider(), qualifier);
		validationOptions = null;
	}
	
	/**
	 * Constructs a host name from an InetSocketAddress.
	 * 
	 * @param inetSocketAddr
	 */
	public HostName(InetSocketAddress inetSocketAddr) {
		if(!inetSocketAddr.isUnresolved()) {
			// we take the resolved addresses from the socket address, even though in here we might have gotten more
			resolvedAddresses = new IPAddress[] {toIPAddress(inetSocketAddr.getAddress(), IPAddressString.DEFAULT_VALIDATION_OPTIONS)};
		}
		// We will parse and normalize as usual.
		// There is no way to know if we are getting a 
		// host name string here or an ip address literal without parsing it ourselves.
		// This is true even when the socket address is resolved, 
		// we have no way of knowing if it started as an ip address literal or not,
		// without parsing the original string.
		int port = inetSocketAddr.getPort();
		String hostStr = inetSocketAddr.getHostString().trim();
		StringBuilder builder = new StringBuilder(hostStr.length() + 6); // 1 ':' and max 5 for port, largest being 65535
		toNormalizedString(port, builder.append(hostStr));
		host = builder.toString();
		validationOptions = DEFAULT_SOCKET_ADDR_VALIDATION_OPTIONS; // we need exportPort to be true in the validation options so ::1:8080 is interpreted as ::1 with port 8080 and not IPv6 ::1:8080
	}

	/**
	 * Constructs a host name from an address with prefix length, which can be null.
	 * 
	 * @param inetAddr
	 */
	public HostName(InetAddress inetAddr, Integer prefixLength) {
		this(toIPAddress(inetAddr, IPAddressString.DEFAULT_VALIDATION_OPTIONS, prefixLength));
	}
	
	/**
	 * Constructs a host name from an InterfaceAddress.
	 * 
	 * @param interfaceAddr
	 */
	public HostName(InterfaceAddress interfaceAddr) {
		this(interfaceAddr.getAddress(), IPAddressNetwork.cacheBits(interfaceAddr.getNetworkPrefixLength()));
	}
	
	/**
	 * Constructs a host name from an IP address.
	 * 
	 * @param inetAddr
	 */
	public HostName(InetAddress inetAddr) {
		this(inetAddr, IPAddressString.DEFAULT_VALIDATION_OPTIONS);
	}
	
	/**
	 * Constructs a host name from an IP address, allowing control over conversion to an IPAddress instance.
	 * 
	 * @param inetAddr
	 */
	public HostName(InetAddress inetAddr, IPAddressStringParameters addressOptions) {
		this(toIPAddress(inetAddr, addressOptions));
	}
	
	private static IPAddress toIPAddress(InetAddress inetAddr, IPAddressStringParameters addressOptions) {
		return inetAddr instanceof Inet4Address ?
				addressOptions.getIPv4Parameters().getNetwork().getAddressCreator().createAddress((Inet4Address) inetAddr) :
				addressOptions.getIPv6Parameters().getNetwork().getAddressCreator().createAddress((Inet6Address) inetAddr);
	}
	
	private static IPAddress toIPAddress(InetAddress inetAddr, IPAddressStringParameters addressOptions, Integer prefixLength) {
		return inetAddr instanceof Inet4Address ?
				addressOptions.getIPv4Parameters().getNetwork().getAddressCreator().createAddress((Inet4Address) inetAddr, prefixLength) :
				addressOptions.getIPv6Parameters().getNetwork().getAddressCreator().createAddress((Inet6Address) inetAddr, prefixLength);
	}
	
	HostName(String hostStr, ParsedHost parsed) {
		host = hostStr;
		parsedHost = parsed;
		validationOptions = null;
	}
	
	/**
	 * Constructs a host name instance from the given string.  
	 * Supports string host names and ip addresses.  
	 * Also allows masks, ports, service name strings, and prefix lengths, both with addresses and host name strings.
	 * Any {@link IPAddressString} format is supported.
	 * @param host
	 */
	public HostName(String host) {
		this(host, DEFAULT_VALIDATION_OPTIONS);
	}

	/**
	 * Similar to {@link #HostName(String)}, but allows you to control which elements are allowed and which are not, 
	 * using the given options.  The default options used by {@link #HostName(String)} are permissive.
	 * @param host
	 * @param options
	 */
	public HostName(String host, HostNameParameters options) {
		if(options == null) {
			throw new NullPointerException();
		}
		validationOptions = options;
		this.host = (host == null) ? "" : host.trim();
	}

	void cacheAddress(IPAddress addr) {
		if(parsedHost == null) {
			parsedHost = new ParsedHost(host, addr.getProvider());
		}
	}

	/**
	 * Supplies the validation options used to validate this host name, whether the default or the one supplied with {@link #HostName(String, HostNameParameters)}
	 * 
	 * @return
	 */
	public HostNameParameters getValidationOptions() {
		return validationOptions;
	}

	/**
	 * Validates that this string is a valid host name or IP address, and if not, throws an exception with a descriptive message indicating why it is not.
	 * @throws HostNameException
	 */
	@Override
	public void validate() throws HostNameException {
		if(parsedHost != null) {
			return;
		}
		if(validationException != null) {
			throw validationException;
		}
		synchronized(this) {
			if(parsedHost != null) {
				return;
			} else if(validationException != null) {
				throw validationException;
			}
			try {
				parsedHost = getValidator().validateHost(this);
			} catch(HostNameException e) {
				validationException = e;
				throw e;
			}
		}
	}
	
	protected HostIdentifierStringValidator getValidator() {
		return Validator.VALIDATOR;
	}

	/**
	 * Returns whether this represents a valid host name or address format.
	 * @return
	 */
	public boolean isValid() {
		if(parsedHost != null) {
			return true;
		} else if(validationException != null) {
			return false;
		}
		try {
			validate();
			return true;
		} catch(HostNameException e) {
			return false;
		}
	}
	
	/**
	 * Returns whether this represents, or resolves to, 
	 * a host or address representing the same host.
	 * 
	 * @return whether this represents or resolves to the localhost host or a loopback address
	 */
	public boolean resolvesToSelf() {
		return isSelf() || (getAddress() != null && resolvedAddresses[0].isLoopback());
	}
	
	/**
	 * Returns whether this represents a host or address representing the same host.
	 * Also see {@link #isLocalHost()} and {@link #isLoopback()}
	 * 
	 * @return whether this is the localhost host or a loopback address
	 */
	public boolean isSelf() {
		return isLocalHost() || isLoopback();
	}
	
	/**
	 * Returns whether this host is "localhost"
	 * @return
	 */
	public boolean isLocalHost() {
		return isValid() && host.equalsIgnoreCase("localhost");
	}
	
	/**
	 * Returns whether this host has the loopback address, such as
	 * [::1] (aka [0:0:0:0:0:0:0:1]) or 127.0.0.1
	 * 
	 * Also see {@link #isSelf()}
	 */
	public boolean isLoopback() {
		return isAddress() && asAddress().isLoopback();
	}
	
	/**
	 * Returns the InetAddress associated with this host.  This will attempt to resolve a host name string if the string is not already an IP address.
	 * 
	 * @return
	 * @throws HostNameException when validation fails
	 * @throws UnknownHostException when resolve fails
	 */
	public InetAddress toInetAddress() throws HostNameException, UnknownHostException {
		validate();
		return toAddress().toInetAddress();
	}
	
	/**
	 * Provides a normalized string which is lowercase for host strings, and which is a normalized string for addresses.
	 * @return
	 */
	@Override
	public String toNormalizedString() {
		String result = normalizedString;
		if(result == null) {
			normalizedString = result = toNormalizedString(false);
		}
		return result;
	}
	
	private String toNormalizedWildcardString() {//used by hashCode
		String result = normalizedWildcardString;
		if(result == null) {
			normalizedWildcardString = result = toNormalizedString(true);
		}
		return result;
	}
	
	private static void translateReserved(IPv6Address addr, String str, StringBuilder builder) {
	//private static CharSequence translateReserved(IPv6Address addr, String str, StringBuilder builder) {
		//This is particularly targeted towards the zone
//		if(!addr.hasZone()) {
//			return str;
//		}
		if(!addr.hasZone()) {
			builder.append(str);
			return;
			//return str;
		}
		
		int index = str.indexOf(IPv6Address.ZONE_SEPARATOR);
		StringBuilder translated = builder;
		//StringBuilder translated = new StringBuilder(((str.length() - index) * 3) + index);
		translated.append(str, 0, index);
		translated.append("%25");
		for(int i = index + 1; i < str.length(); i++) {
			char c = str.charAt(i);
			if(Validator.isReserved(c)) {
				translated.append('%');
				IPAddressSegment.toUnsignedString(c, 16, translated);
			} else {
				translated.append(c);
			}
		}
		//return translated;
		return;
	}
	
	private static String toNormalizedString(IPAddress addr, int port) {
		StringBuilder builder = new StringBuilder();
		toNormalizedString(addr, false, builder);
		toNormalizedString(port, builder);
		return builder.toString();
	}
	
	private String toNormalizedString(boolean wildcard) {
		if(isValid()) {
			StringBuilder builder = new StringBuilder();
			if(isAddress()) {
				toNormalizedString(asAddress(), wildcard, builder);
			} else if(isAddressString()) {
				builder.append(asAddressString().toNormalizedString());
			} else {
				builder.append(parsedHost.getHost());
				/*
				 * If prefix or mask is supplied and there is an address, it is applied directly to the address provider, so 
				 * we need only check for those things here
				 * 
				 * Also note that ports and prefix/mask cannot appear at the same time, so this does not interfere with the port code below.
				 */
				Integer networkPrefixLength = parsedHost.getEquivalentPrefixLength();
				if(networkPrefixLength != null) {
					builder.append(IPAddress.PREFIX_LEN_SEPARATOR);
					IPAddressSegment.toUnsignedString(networkPrefixLength, 10, builder);
				} else {
					IPAddress mask = parsedHost.getMask();
					if(mask != null) {
						builder.append(IPAddress.PREFIX_LEN_SEPARATOR).append(mask.toNormalizedString());
					}
				}
			}
			Integer port = parsedHost.getPort();
			if(port != null) {
				toNormalizedString(port, builder);
			} else {
				String service = parsedHost.getService();
				if(service != null) {
					builder.append(PORT_SEPARATOR).append(service);
				}
			}
			return builder.toString();
		}
		return host;
	}
	
	private static void toNormalizedString(int port, StringBuilder builder) {
		builder.append(PORT_SEPARATOR);
		IPAddressSegment.toUnsignedString(port, 10, builder);
	}
	
	private static void toNormalizedString(IPAddress addr, boolean wildcard, StringBuilder builder) {
		if(addr.isIPv6()) {
			if(!wildcard && addr.isPrefixed()) {//prefix needs to be outside the brackets
				builder.append(IPV6_START_BRACKET);
				String normalized = addr.toNormalizedString();
				int index = normalized.indexOf(IPAddress.PREFIX_LEN_SEPARATOR);
				translateReserved(addr.toIPv6(), normalized.substring(0, index), builder);
				builder.append(IPV6_END_BRACKET).append(normalized.substring(index));
			} else {
				builder.append(IPV6_START_BRACKET);
				translateReserved(addr.toIPv6(), addr.toNormalizedWildcardString(), builder);
				builder.append(IPV6_END_BRACKET);
			}
		} else {
			builder.append(wildcard ? addr.toNormalizedWildcardString() : addr.toNormalizedString());
		}
	}
	
	/**
	 * Returns true if the given object is a host name and {@link #matches(HostName)} this one.
	 */
	@Override
	public boolean equals(Object o) {
		return o instanceof HostName && matches((HostName) o);
	}

	@Override
	public int hashCode() {
		return toNormalizedWildcardString().hashCode();
	}
	
	/**
	 * Returns an array of normalized strings for this host name instance.
	 * 
	 * If this represents an IP address, the address segments are separated into the returned array.
	 * If this represents a host name string, the domain name segments are separated into the returned array,
	 * with the top-level domain name (right-most segment) as the last array element.
	 * 
	 * The individual segment strings are normalized in the same way as {@link #toNormalizedString()}
	 * 
	 * Ports, service name strings, prefix lengths, and masks are all omitted from the returned array.
	 * 
	 * @return
	 */
	public String[] getNormalizedLabels() {
		if(isValid()) {
			return parsedHost.getNormalizedLabels();
		} else if(host.length() == 0) {
			return new String[0];
		}
		return new String[] {host};
	}
	
	/**
	 * Returns the host string normalized but without port, service, prefix or mask.
	 * 
	 * If an address, returns the address string normalized, but without port, service, prefix, mask, or brackets for IPv6.
	 * 
	 * To get a normalized string encompassing all details, use toNormalizedString()
	 * 
	 * If not a valid host, returns null
	 * 
	 * @return
	 */
	public String getHost() {
		if(isValid()) {
			return parsedHost.getHost();
		}
		return null;
	}
	
	/**
	 * Returns whether the given host matches this one.  For hosts to match, they must represent the same addresses or have the same host names.
	 * Hosts are not resolved when matching.  Also, hosts must have the same port and service.  They must have the same masks if they are host names.
	 * Even if two hosts are invalid, they match if they have the same invalid string.
	 * 
	 * @param host
	 * @return
	 */
	public boolean matches(HostName host) {
		if(this == host) {
			return true;
		}
		if(isValid()) {
			if(host.isValid()) {
				ParsedHost parsedHost = this.parsedHost;
				ParsedHost otherParsedHost = host.parsedHost;
				if(parsedHost.isAddressString()) {
					return otherParsedHost.isAddressString()
							&& parsedHost.asGenericAddressString().equals(otherParsedHost.asGenericAddressString())
							&& Objects.equals(parsedHost.getPort(), otherParsedHost.getPort())
							&& Objects.equals(parsedHost.getService(), otherParsedHost.getService());
				}
				if(otherParsedHost.isAddressString()) {
					return false;
				}
				String thisHost = parsedHost.getHost();
				String otherHost = otherParsedHost.getHost();
				if(!thisHost.equals(otherHost)) {
					return false;
				}
				return Objects.equals(parsedHost.getEquivalentPrefixLength(), otherParsedHost.getEquivalentPrefixLength()) &&
						Objects.equals(parsedHost.getMask(), otherParsedHost.getMask()) &&
						Objects.equals(parsedHost.getPort(), otherParsedHost.getPort()) &&
						Objects.equals(parsedHost.getService(), otherParsedHost.getService());
			}
			return false;
		}
		return !host.isValid() && toString().equals(host.toString());
	}

	@Override
	public int compareTo(HostName other) {
		if(isValid()) {
			if(other.isValid()) {
				ParsedHost parsedHost = this.parsedHost;
				ParsedHost otherParsedHost = other.parsedHost;
				if(parsedHost.isAddressString()) {
					if(otherParsedHost.isAddressString()) {
						int result = parsedHost.asGenericAddressString().compareTo(otherParsedHost.asGenericAddressString());
						if(result != 0) {
							return result;
						}
						//fall through to compare ports
					} else {
						return -1;
					}
				} else if(otherParsedHost.isAddressString()) {
					return 1;
				} else {
					//both are non-address hosts
					String normalizedLabels[] = parsedHost.getNormalizedLabels();
					String otherNormalizedLabels[] = otherParsedHost.getNormalizedLabels();
					int oneLen = normalizedLabels.length;
					int twoLen = otherNormalizedLabels.length;
					for(int i = 1, minLen = Math.min(oneLen, twoLen); i <= minLen; i++) {
						String one = normalizedLabels[oneLen - i];
						String two = otherNormalizedLabels[twoLen - i];
						int result = one.compareTo(two);
						if(result != 0) {
							return result;
						}
					}
					if(oneLen != twoLen) {
						return oneLen - twoLen;
					}
					
					//keep in mind that hosts can has masks/prefixes or ports, but not both
					Integer networkPrefixLength = parsedHost.getEquivalentPrefixLength();
					Integer otherPrefixLength = otherParsedHost.getEquivalentPrefixLength();
					if(networkPrefixLength != null) {
						if(otherPrefixLength != null) {
							if(networkPrefixLength.intValue() != otherPrefixLength.intValue()) {
								return otherPrefixLength - networkPrefixLength;
							}
							//fall through to compare ports
						} else {
							return 1;
						}
					} else {
						if(otherPrefixLength != null) {
							return -1;
						}
						IPAddress mask = parsedHost.getMask();
						IPAddress otherMask = otherParsedHost.getMask();
						if(mask != null) {
							if(otherMask != null) {
								int ret = mask.compareTo(otherMask);
								if(ret != 0) {
									return ret;
								}
								//fall through to compare ports
							} else {
								return 1;
							}
						} else {
							if(otherMask != null) {
								return -1;
							}
							//fall through to compare ports
						}
					}//end non-address host compare
				}
				
				//two equivalent address strings or two equivalent hosts, now check port and service names
				Integer portOne = parsedHost.getPort();
				Integer portTwo = otherParsedHost.getPort();
				if(portOne != null) {
					if(portTwo != null) {
						int ret = portOne - portTwo;
						if(ret != 0) {
							return ret;
						}
					} else {
						return 1;
					}
				} else if(portTwo != null) {
					return -1;
				}
				String serviceOne = parsedHost.getService();
				String serviceTwo = otherParsedHost.getService();
				if(serviceOne != null) {
					if(serviceTwo != null) {
						int ret = serviceOne.compareTo(serviceTwo);
						if(ret != 0) {
							return ret;
						}
					} else {
						return 1;
					}
				} else if(serviceTwo != null) {
					return -1;
				}
				return 0;
			} else {
				return 1;
			}
		} else if(other.isValid()) {
			return -1;
		}
		return toString().compareTo(other.toString());
	}

	@Deprecated
	public boolean isAddress(IPVersion version) {
		return isValid() && parsedHost.isAddressString() && parsedHost.asAddress(version) != null;
	}

	/**
	 * Returns whether this host name is a string representing an valid specific IP address or subnet.
	 * 
	 * @return
	 */
	public boolean isAddress() {
		return isAddressString() && parsedHost.asAddress() != null; 
	}

	/**
	 * Returns whether this host name is a string representing an IP address or subnet.
	 * 
	 * @return
	 */
	public boolean isAddressString() {
		return isValid() && parsedHost.isAddressString();
	}

	/**
	 * Whether the address represents the set all all valid IP addresses (as opposed to an empty string, a specific address, a prefix length, or an invalid format).
	 * 
	 * @return whether the address represents the set all all valid IP addresses
	 */
	public boolean isAllAddresses() {
		return isAddressString() && parsedHost.getAddressProvider().isProvidingAllAddresses();
	}

	/**
	 * Whether the address represents a valid IP address network prefix (as opposed to an empty string, an address with or without a prefix, or an invalid format).
	 * 
	 * @return whether the address represents a valid IP address network prefix
	 */
	public boolean isPrefixOnly() {
		return isAddressString() && parsedHost.getAddressProvider().isProvidingPrefixOnly();
	}

	/**
	 * Returns true if the address is empty (zero-length).
	 * @return
	 */
	public boolean isEmpty() {
		return isAddressString() && parsedHost.getAddressProvider().isProvidingEmpty();
	}

	/**
	 * If a port was supplied, returns the port, otherwise returns null
	 * 
	 * @return
	 */
	public Integer getPort() {
		return isValid() ? parsedHost.getPort() : null;
	}

	/**
	 * If a service name was supplied, returns the service name, otherwise returns null
	 * 
	 * @return
	 */
	public String getService() {
		return isValid() ? parsedHost.getService() : null;
	}

	/**
	 * Returns the exception thrown for invalid ipv6 literal or invalid reverse DNS hosts.
	 * 
	 * This method will return non-null when this host is valid, so no HostException is thrown,
	 * but a secondary address within the host is not valid.
	 *  
	 * @return
	 */
	public AddressStringException getAddressStringException() {
		if(isValid()) {
			return parsedHost.getAddressStringException();
		}
		return null;
	}
	
	/**
	 * Returns whether this host name is an Uniform Naming Convention IPv6 literal host name.
	 * 
	 * @return
	 */
	public boolean isUNCIPv6Literal() {
		return isValid() && parsedHost.isUNCIPv6Literal();
	}
	
	/**
	 * Returns whether this host name is a reverse DNS string host name.
	 * 
	 * @return
	 */
	public boolean isReverseDNS() {
		return isValid() && parsedHost.isReverseDNS();
	}

	/**
	 * If this represents an ip address or represents any valid IPAddressString, returns the corresponding address string.
	 * Otherwise, returns null.  Note that translation includes prefix lengths and IPv6 zones.  
	 * This does not resolve addresses.  Call {@link #toAddress()} or {@link #getAddress()} to get the resolved address.
	 * @return
	 */
	public IPAddressString asAddressString() {
		if(isAddressString()) {
			return parsedHost.asGenericAddressString(); // this is for address string not convertible to address
		}
		return null;
	}

	/**
	 * If this represents an ip address, returns that address.  Otherwise, returns null.  
	 * Note that translation includes prefix lengths and IPv6 zones.
	 * <p>
	 * This does not resolve addresses or return resolved addresses.
	 * Call {@link #toAddress()} or {@link #getAddress()} to get the resolved address.
	 * <p>
	 * In cases such as IPv6 literals and reverse DNS hosts, you can check the relevant methods isIpv6Literal or isReverseDNS,
	 * in which case this method should return the associated address.  If this method returns null then an exception occurred
	 * when producing the associated address, and that exception is available from getAddressStringException.
	 * 
	 * @return
	 */
	public IPAddress asAddress() {
		if(isAddress()) {
			return parsedHost.asAddress();
		}
		return null;
	}
	
	/**
	 * If this represents an ip address, returns that address.
	 * Otherwise, returns null.  Call {@link #toAddress()} or {@link #getAddress()} to get the resolved address.
	 * 
	 * @return
	 */
	@Deprecated
	public IPAddress asAddress(IPVersion version) {
		if(isAddress(version)) {
			return parsedHost.asAddress(version);
		}
		return null;
	}

	/**
	 * If a prefix length was supplied, either as part of an address or as part of a domain (in which case the prefix applies to any resolved address), 
	 * then returns that prefix length.  Otherwise, returns null.
	 */
	public Integer getNetworkPrefixLength() {
		if(isAddress()) {
			return parsedHost.asAddress().getNetworkPrefixLength();
		} else if(isAddressString()) {
			return parsedHost.asGenericAddressString().getNetworkPrefixLength();
		}
		return isValid() ? parsedHost.getEquivalentPrefixLength() : null;
	}
	
	/**
	 * If a mask was provided with this host name, this returns the resulting mask value.
	 * 
	 * @return
	 */
	public IPAddress getMask() {
		if(isValid()) {
			if(parsedHost.isAddressString()) {
				return parsedHost.getAddressProvider().getProviderMask();
			}
			return parsedHost.getMask();
		}
		return null;
	}

	/**
	 * Similar to {@link #toInetAddress()} but does not throw, instead returns null whenever not a valid address.
	 * This method does not resolve hosts.  For that, call {@link #toAddress()} and then {@link IPAddress#toInetAddress()}
	 * 
	 * @return
	 */
	public InetAddress asInetAddress() {
		if(isValid() && isAddressString()) {
			IPAddress ipAddr = asAddress();
            if(ipAddr != null) {
            	return ipAddr.toInetAddress();
            }
		}
		return null;
	}
	
	/**
	 * Returns the InetSocketAddress for this host.  A host must have an associated port,
	 * or a service name string that is mapped to a port using the provided service mapper, 
	 * to have a corresponding InetSocketAddress.
	 * <p>
	 * If there is on associated port, then this returns null.
	 * <p>
	 * Note that host name strings are not resolved when using this method.
	 * 
	 * @param serviceMapper maps service name strings to ports.  
	 * 	Returns null when a service string has no mapping, otherwise returns the port for a given service.
	 * 	You can use a project like netdb to provide a service mapper lambda, https://github.com/jnr/jnr-netdb
	 * @return the socket address, or null if no such address.
	 */
	public InetSocketAddress asInetSocketAddress(Function<String, Integer> serviceMapper) {
		if(isValid()) {
			Integer port = getPort();
			if(port == null && serviceMapper != null) {
				String service = getService();
				if(service != null) {
					port = serviceMapper.apply(service);
				}
			}
			if(port != null) {
				IPAddress ipAddr;
				if(isAddressString() && (ipAddr = asAddress()) != null) {
					return new InetSocketAddress(ipAddr.toInetAddress(), port);
				} else {
					return new InetSocketAddress(getHost(), port);
				}
			}
		}
		return null;
	}
	
	/**
	 * Returns the InetSocketAddress for this host.  A host must have an associated port to have a corresponding InetSocketAddress.
	 * <p>
	 * If there is on associated port, then this returns null.
	 * <p>
	 * Note that host name strings are not resolved when using this method.
	 * 
	 * @return the socket address, or null if no such address.
	 */
	public InetSocketAddress asInetSocketAddress() {
		return asInetSocketAddress(null);
	}

	/**
	 * Similar to {@link #toAddress()}, however in the case where the host is resolved,
	 * this method returns all resolved addresses rather than the primary resolved address.
	 * In the cases where {@link #toAddress()} returns null, this returns an empty array,
	 * and in the cases where this host represents an IP address, this method returns an array of length 1 with that address.
	 * 
	 * @return
	 * @throws UnknownHostException
	 * @throws HostNameException
	 */
	public IPAddress[] toAllAddresses() throws UnknownHostException, HostNameException {
		return toAddresses();
	}
	
	/**
	 * If this represents an IP address, returns that address.
	 * If this represents a host, returns the resolved ip address of that host.
	 * Otherwise, returns null, but only for strings that are considered valid address strings but cannot be converted to address objects.
	 * 
	 * This method will throw exceptions for invalid formats and failures to resolve the address.  The equivalent method {@link #getAddress()} will simply return null rather than throw those exceptions.
	 * 
	 * If you wish to get the represented address and avoid DNS resolution, use {@link #asAddress()} or {@link #asAddressString()}
	 * 
	 * @return
	 */
	@Override
	public IPAddress toAddress() throws UnknownHostException, HostNameException {
		IPAddress addrs[] = toAddresses();
		return addrs.length == 0 ? null : addrs[0];
	}
	
	
	private IPAddress[] toAddresses() throws UnknownHostException, HostNameException {
		IPAddress addrs[] = resolvedAddresses;
		if(addrs == null) {
			//note that validation handles empty address resolution
			validate();
			synchronized(this) {
				addrs = resolvedAddresses;
				if(addrs == null) {
					if(parsedHost.isAddressString()) {
						addrs = new IPAddress[] {parsedHost.asAddress()};
						//note there is no need to apply prefix or mask here, it would have been applied to the address already
					} else {
						String strHost = parsedHost.getHost();
						if(strHost.length() == 0 && !validationOptions.emptyIsLoopback) {
							addrs = EMPTY_ADDRS;
						} else {
							//Note we do not set resolvedIsNull, so we will attempt to resolve again if the previous attempt threw an exception
							InetAddress inetAddresses[] = InetAddress.getAllByName(strHost);
							addrs = new IPAddress[inetAddresses.length];
							for(int j = 0; j < inetAddresses.length; j++) {
								InetAddress inetAddress = inetAddresses[j];
								byte bytes[] = inetAddress.getAddress();
								Integer networkPrefixLength = parsedHost.getNetworkPrefixLength();
								if(networkPrefixLength == null) {
									IPAddress mask = parsedHost.getMask();
									if(mask != null) {
										byte maskBytes[] = mask.getBytes();
										if(maskBytes.length == bytes.length) {
											for(int i = 0; i < bytes.length; i++) {
												bytes[i] &= maskBytes[i];
											}
											networkPrefixLength = mask.getBlockMaskPrefixLength(true);
										}
									}
								}
								IPAddressStringParameters addressParams = validationOptions.addressOptions;
								if(bytes.length == IPv6Address.BYTE_COUNT) {
									IPv6AddressCreator creator = addressParams.getIPv6Parameters().getNetwork().getAddressCreator();
									addrs[j] = creator.createAddressInternal(bytes, networkPrefixLength, null, this); /* address creation */
								} else {
									if(networkPrefixLength != null && networkPrefixLength > IPv4Address.BIT_COUNT) {
										networkPrefixLength = IPAddressSection.cacheBits(IPv4Address.BIT_COUNT);
									}
									IPv4AddressCreator creator = addressParams.getIPv4Parameters().getNetwork().getAddressCreator();
									addrs[j] = creator.createAddressInternal(bytes, networkPrefixLength, this); /* address creation */
								}
							}
							
						}
					}
					resolvedAddresses = addrs;
				}
			}
		}
		return addrs;
	}

	/**
	 * If this represents an ip address, returns that address.
	 * If this represents a host, returns the resolved ip address of that host.
	 * Otherwise, returns null.
	 * 
	 * If you wish to get the represented address and avoid DNS resolution, use {@link #asAddress()} or {@link #asAddressString()}
	 * 
	 * @return
	 */
	@Override
	public IPAddress getAddress() {
		try {
			return toAddress();
		} catch(HostNameException | UnknownHostException e) {
			//call toResolvedAddress if you wish to see this exception
			//HostNameException objects are cached in validate and can be seen by calling validate
		}
		return null;
	}
	
	/**
	 * Returns the string used to construct the object.
	 */
	@Override
	public String toString() {
		return host;
	}
}
