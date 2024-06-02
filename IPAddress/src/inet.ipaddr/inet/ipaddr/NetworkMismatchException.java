/*
 * Copyright 2016-2019 Sean C Foley
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

import inet.ipaddr.format.AddressItem;

/**
 * Thrown when two different networks in use by the same address object are in conflict.
 * @author User
 *
 */
public class NetworkMismatchException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	private static String errorMessage = getMessage("ipaddress.address.error");
	
	static String getMessage(String key) {
		return AddressStringException.getMessage(key);
	}
	
	public NetworkMismatchException(AddressItem one) {
		super(one + ", " + errorMessage + " " + getMessage("ipaddress.error.mixedNetworks"));
	}
	
	public NetworkMismatchException(AddressItem one, AddressItem two) {
		super(one + ", " + two + ", " + errorMessage + " " + getMessage("ipaddress.error.mixedNetworks"));
	}
}
