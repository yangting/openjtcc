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
import org.bytesoft.openjtcc.supports.rmi.RemoteInvocationRequest;

public class RemoteInvocationRequestImpl implements RemoteInvocationRequest, Serializable {
	private static final long serialVersionUID = 1L;
	private RemoteInvocationType invocationType;
	private String declaringClass;
	private String interfaceClass;
	private String methodName;
	private String[] parameterTypes;
	private Object[] parameterValues;
	private Object transactionContext;
	private TerminalKey terminalKey;
	private String beanId;

	public String getDeclaringClass() {
		return declaringClass;
	}

	public void setDeclaringClass(String declaringClass) {
		this.declaringClass = declaringClass;
	}

	public String getMethodName() {
		return methodName;
	}

	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	public String[] getParameterTypes() {
		return parameterTypes;
	}

	public void setParameterTypes(String[] parameterTypes) {
		this.parameterTypes = parameterTypes;
	}

	public Object getTransactionContext() {
		return transactionContext;
	}

	public void setTransactionContext(Object transactionContext) {
		this.transactionContext = transactionContext;
	}

	public Object[] getParameterValues() {
		return parameterValues;
	}

	public void setParameterValues(Object[] parameterValues) {
		this.parameterValues = parameterValues;
	}

	public RemoteInvocationType getInvocationType() {
		return invocationType;
	}

	public void setInvocationType(RemoteInvocationType invocationType) {
		this.invocationType = invocationType;
	}

	public String getInterfaceClass() {
		return interfaceClass;
	}

	public void setInterfaceClass(String interfaceClass) {
		this.interfaceClass = interfaceClass;
	}

	public TerminalKey getTerminalKey() {
		return terminalKey;
	}

	public void setTerminalKey(TerminalKey terminalKey) {
		this.terminalKey = terminalKey;
	}

	public String getBeanId() {
		return beanId;
	}

	public void setBeanId(String beanId) {
		this.beanId = beanId;
	}

	public String toString() {
		return String.format("Rmi-Request [%s] bean: %s, method: %s", //
				this.terminalKey, this.beanId, this.methodName);
	}
}
