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
package org.bytesoft.openjtcc.supports.dubbo.internal;

import java.io.Serializable;

import org.bytesoft.openjtcc.common.TerminalKey;
import org.bytesoft.openjtcc.supports.dubbo.RemoteInvocationType;
import org.bytesoft.openjtcc.supports.rmi.RemoteInvocationResponse;

public class RemoteInvocationResponseImpl implements RemoteInvocationResponse, Serializable {
	private static final long serialVersionUID = 1L;
	private transient RemoteInvocationRequestImpl request;
	private RemoteInvocationType invocationType;
	private Object result;
	private Object transactionContext;
	private Throwable throwable;
	private boolean failure;
	private TerminalKey terminalKey;

	public RemoteInvocationResponseImpl() {
	}

	public Object getResult() {
		return result;
	}

	public void setResult(Object result) {
		this.result = result;
	}

	public Object getTransactionContext() {
		return transactionContext;
	}

	public void setTransactionContext(Object transactionContext) {
		this.transactionContext = transactionContext;
	}

	public Throwable getThrowable() {
		return throwable;
	}

	public void setThrowable(Throwable throwable) {
		this.failure = true;
		this.throwable = throwable;
	}

	public boolean isFailure() {
		return failure;
	}

	public RemoteInvocationType getInvocationType() {
		return invocationType;
	}

	public void setInvocationType(RemoteInvocationType invocationType) {
		this.invocationType = invocationType;
	}

	public TerminalKey getTerminalKey() {
		return terminalKey;
	}

	public void setTerminalKey(TerminalKey terminalKey) {
		this.terminalKey = terminalKey;
	}

	public RemoteInvocationRequestImpl getRequest() {
		return request;
	}

	public void setRequest(RemoteInvocationRequestImpl request) {
		this.request = request;
	}

	public String toString() {
		return String.format("Rmi-Response [%s] bean: %s, method: %s", this.terminalKey,//
				this.request == null ? "" : this.request.getBeanId(),//
				this.request == null ? "" : this.request.getMethodName());
	}
}
