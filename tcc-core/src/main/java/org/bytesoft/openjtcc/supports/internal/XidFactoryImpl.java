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
package org.bytesoft.openjtcc.supports.internal;

import java.util.concurrent.atomic.AtomicLong;

import org.bytesoft.openjtcc.common.TerminalKey;
import org.bytesoft.openjtcc.xa.XidFactory;
import org.bytesoft.openjtcc.xa.XidImpl;
import org.bytesoft.utils.ByteUtils;

public class XidFactoryImpl implements XidFactory {

	private final AtomicLong atomic = new AtomicLong();
	private TerminalKey terminalKey;

	public XidImpl createGlobalXid() {
		byte[] global = new byte[18];
		int appcode = terminalKey.getApplication().hashCode();
		int endcode = terminalKey.getEndpoint().hashCode();
		byte[] appByteArray = ByteUtils.intToByteArray(appcode);
		byte[] endByteArray = ByteUtils.intToByteArray(endcode);

		long millis = System.currentTimeMillis();
		byte[] millisByteArray = ByteUtils.longToByteArray(millis);
		short index = (short) (atomic.incrementAndGet() % Short.MAX_VALUE);
		byte[] atomicByteArray = ByteUtils.shortToByteArray(index);

		System.arraycopy(appByteArray, 0, global, 0, 4);
		System.arraycopy(endByteArray, 0, global, 4, 4);
		System.arraycopy(millisByteArray, 0, global, 8, 8);
		System.arraycopy(atomicByteArray, 0, global, 16, 2);

		return new XidImpl(global);
	}

	public XidImpl createGlobalXid(byte[] globalTransactionId) {
		if (globalTransactionId == null) {
			throw new IllegalArgumentException("The globalTransactionId cannot be null.");
		} else if (globalTransactionId.length > XidImpl.MAXGTRIDSIZE) {
			throw new IllegalArgumentException("The length of globalTransactionId cannot exceed 64 bytes.");
		}
		byte[] global = new byte[globalTransactionId.length];
		System.arraycopy(globalTransactionId, 0, global, 0, global.length);
		return new XidImpl(global);
	}

	public XidImpl createBranchXid(XidImpl globalXid) {
		if (globalXid == null) {
			throw new IllegalArgumentException("Xid cannot be null.");
		} else if (globalXid.getGlobalTransactionId() == null) {
			throw new IllegalArgumentException("The globalTransactionId cannot be null.");
		} else if (globalXid.getGlobalTransactionId().length > XidImpl.MAXGTRIDSIZE) {
			throw new IllegalArgumentException("The length of globalTransactionId cannot exceed 64 bytes.");
		}

		byte[] global = new byte[globalXid.getGlobalTransactionId().length];
		System.arraycopy(globalXid.getGlobalTransactionId(), 0, global, 0, global.length);

		byte[] branch = new byte[10];
		int appcode = terminalKey.getApplication().hashCode();
		int endcode = terminalKey.getEndpoint().hashCode();
		byte[] appByteArray = ByteUtils.intToByteArray(appcode);
		byte[] endByteArray = ByteUtils.intToByteArray(endcode);

		short index = (short) (atomic.incrementAndGet() % Short.MAX_VALUE);
		byte[] atomicByteArray = ByteUtils.shortToByteArray(index);

		System.arraycopy(appByteArray, 0, branch, 0, 4);
		System.arraycopy(endByteArray, 0, branch, 4, 4);
		System.arraycopy(atomicByteArray, 0, branch, 8, 2);
		return new XidImpl(global, branch);
	}

	public XidImpl createBranchXid(XidImpl globalXid, byte[] branchQualifier) {
		if (globalXid == null) {
			throw new IllegalArgumentException("Xid cannot be null.");
		} else if (globalXid.getGlobalTransactionId() == null) {
			throw new IllegalArgumentException("The globalTransactionId cannot be null.");
		} else if (globalXid.getGlobalTransactionId().length > XidImpl.MAXGTRIDSIZE) {
			throw new IllegalArgumentException("The length of globalTransactionId cannot exceed 64 bytes.");
		}

		if (branchQualifier == null) {
			throw new IllegalArgumentException("The branchQulifier cannot be null.");
		} else if (branchQualifier.length > XidImpl.MAXBQUALSIZE) {
			throw new IllegalArgumentException("The length of branchQulifier cannot exceed 64 bytes.");
		}

		byte[] global = new byte[globalXid.getGlobalTransactionId().length];
		System.arraycopy(globalXid.getGlobalTransactionId(), 0, global, 0, global.length);

		return new XidImpl(global, branchQualifier);
	}

	public TerminalKey getTerminalKey() {
		return terminalKey;
	}

	public void setTerminalKey(TerminalKey terminalKey) {
		this.terminalKey = terminalKey;
	}

}
