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
package org.bytesoft.openjtcc.xa;

import java.io.Serializable;
import java.util.Arrays;

import javax.transaction.xa.Xid;

import org.bytesoft.utils.ByteUtils;

public class XidImpl implements Xid, Serializable {
	private static final long serialVersionUID = 1L;

	public static final int xidFormatId = 13923;

	private final int formatId = xidFormatId;
	private byte[] globalTransactionId;
	private byte[] branchQualifier;

	public XidImpl() {
		this(new byte[0], new byte[0]);
	}

	public XidImpl(byte[] global) {
		this(global, new byte[0]);
	}

	public XidImpl(byte[] global, byte[] branch) {
		if (global == null) {
			throw new IllegalArgumentException("全局事务ID(globalTransactionId)不能为空.");
		} else if (global.length > MAXGTRIDSIZE) {
			throw new IllegalArgumentException("全局事务ID(globalTransactionId)长度不能超过64.");
		}

		if (branch == null) {
			throw new IllegalArgumentException("事务分支标识(branchQualifier)不能为空.");
		} else if (branch.length > MAXBQUALSIZE) {
			throw new IllegalArgumentException("事务分支标识(branchQualifier)长度不能超过64.");
		}
		this.globalTransactionId = global;
		this.branchQualifier = branch;
	}

	public byte[] getBranchQualifier() {
		return this.branchQualifier;
	}

	public int getFormatId() {
		return this.formatId;
	}

	public byte[] getGlobalTransactionId() {
		return this.globalTransactionId;
	}

	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + formatId;
		result = prime * result + Arrays.hashCode(branchQualifier);
		result = prime * result + Arrays.hashCode(globalTransactionId);
		return result;
	}

	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		} else if (obj == null) {
			return false;
		} else if (getClass() != obj.getClass()) {
			return false;
		}
		XidImpl other = (XidImpl) obj;
		if (formatId != other.formatId) {
			return false;
		} else if (Arrays.equals(branchQualifier, other.branchQualifier) == false) {
			return false;
		} else if (Arrays.equals(globalTransactionId, other.globalTransactionId) == false) {
			return false;
		}
		return true;
	}

	public String toString() {
		String global = this.globalTransactionId == null ? null : ByteUtils.byteArrayToString(this.globalTransactionId);
		String branch = this.branchQualifier == null ? null : ByteUtils.byteArrayToString(this.branchQualifier);
		return String.format("%s-%s-%s", this.formatId, global, branch);
	}

}
