/**
 * Copyright 2014 yangming.liu<liuyangming@gmail.com>.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, see <http://www.gnu.org/licenses/>.
 */
package org.bytesoft.openjtcc.common;

import java.io.Serializable;
import java.util.Arrays;

public class PropagationKey implements Cloneable, Serializable {
	private static final long serialVersionUID = 1L;

	private byte[] instanceKey;

	public PropagationKey(byte[] bytes) {
		this.instanceKey = bytes;
	}

	public byte[] getInstanceKey() {
		return instanceKey;
	}

	public void setInstanceKey(byte[] instanceKey) {
		this.instanceKey = instanceKey;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.instanceKey);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		} else if (PropagationKey.class.isInstance(obj) == false) {
			return false;
		}
		PropagationKey that = (PropagationKey) obj;
		return Arrays.equals(this.instanceKey, that.instanceKey);
	}
}
