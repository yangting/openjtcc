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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.SocketException;
import java.rmi.RemoteException;

import org.bytesoft.openjtcc.common.TerminalKey;
import org.bytesoft.openjtcc.common.TransactionContext;
import org.bytesoft.openjtcc.remote.Cleanupable;
import org.bytesoft.openjtcc.remote.RemoteTerminator;
import org.bytesoft.openjtcc.supports.dubbo.RemoteInvocationService;
import org.bytesoft.openjtcc.supports.dubbo.RemoteInvocationServiceMarshaller;
import org.bytesoft.openjtcc.supports.dubbo.RemoteInvocationType;
import org.bytesoft.openjtcc.supports.serialize.TerminatorInfo;
import org.bytesoft.openjtcc.xa.XidImpl;
import org.bytesoft.utils.CommonUtils;

public class RemoteTerminatorHandler implements InvocationHandler {
	private TerminatorInfo remoteTerminatorInfo;
	private RemoteInvocationService remoteInvocationService;
	private RemoteInvocationServiceMarshaller remoteServiceFactory;

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		String methodName = method.getName();

		if (Object.class.equals(method.getDeclaringClass())) {
			return method.invoke(this, args);
		} else {
			try {
				Class<?> currentClass = this.getClass();
				Method nativeMethod = currentClass.getDeclaredMethod(methodName, method.getParameterTypes());
				return nativeMethod.invoke(this, args);
			} catch (Exception ex) {
				// ignore
			}
		}

		RemoteInvocationRequestImpl request = new RemoteInvocationRequestImpl();
		RemoteInvocationResponseImpl response = null;
		try {
			RemoteInvocationType invocationType = this.getRemoteInvocationType(method);
			request.setInvocationType(invocationType);
			request.setDeclaringClass(method.getDeclaringClass().getName());
			request.setInterfaceClass(RemoteTerminator.class.getSimpleName());
			request.setMethodName(methodName);
			Class<?>[] types = method.getParameterTypes();
			String[] parameterTypes = new String[types.length];
			for (int i = 0; i < types.length; i++) {
				parameterTypes[i] = types[i].getName();
			}
			request.setParameterTypes(parameterTypes);
			request.setParameterValues(args);

			TransactionContext transactionContext = new TransactionContext();
			XidImpl branchXid = this.remoteTerminatorInfo.getBranchXid();
			// XidImpl globalXid = new XidImpl(branchXid.getGlobalTransactionId());
			transactionContext.setCurrentXid(branchXid);
			// transactionContext.setGlobalXid(globalXid);
			request.setTransactionContext(transactionContext);

			if (this.remoteInvocationService == null) {
				try {
					this.remoteInvocationService = this.remoteServiceFactory
							.unmarshallRemoteInvocationService(this.remoteTerminatorInfo);
					if (this.remoteInvocationService == null) {
						throw new RemoteException("cannot create a remote stub object.");
					}
				} catch (RemoteException ex) {
					throw ex;
				} catch (Exception ex) {
					throw new RemoteException("cannot create a remote stub object.", ex);
				}
			}
			response = this.remoteInvocationService.invoke(request);
		} catch (Throwable ex) {
			response = new RemoteInvocationResponseImpl();
			response.setRequest(request);
			response.setInvocationType(request.getInvocationType());
			Throwable thrown = this.getFinalResponseThrowable(ex);
			response.setThrowable(thrown);
			throw response.getThrowable();
		}
		if (response.isFailure()) {
			Throwable thrown = this.getFinalResponseThrowable(response.getThrowable());
			throw thrown;
		} else {
			return response.getResult();
		}
	}

	private Throwable getFinalResponseThrowable(Throwable ex) throws Throwable {
		Throwable thrown = null;
		if (ex instanceof UndeclaredThrowableException) {
			UndeclaredThrowableException utex = (UndeclaredThrowableException) ex;
			thrown = this.transformException(utex.getUndeclaredThrowable());
		} else if (ex instanceof InvocationTargetException) {
			InvocationTargetException itex = (InvocationTargetException) ex;
			thrown = this.transformException(itex.getTargetException());
		} else {
			thrown = this.transformException(ex);
		}
		return thrown;
	}

	private Throwable transformException(Throwable ex) throws Throwable {
		if (ex instanceof UndeclaredThrowableException) {
			UndeclaredThrowableException utex = (UndeclaredThrowableException) ex;
			return this.transformException(utex.getUndeclaredThrowable());
		} else if (ex instanceof InvocationTargetException) {
			InvocationTargetException itex = (InvocationTargetException) ex;
			return this.transformException(itex.getTargetException());
		} else if (ex instanceof RemoteException) {
			return ex;
		}
		boolean isKindOfRemoteException = false;
		if (ex.getClass().getSimpleName().equals("RemoteException")) {
			isKindOfRemoteException = true;
		} else if (ex.getClass().getSimpleName().equals("RpcException")) {
			isKindOfRemoteException = true;
		} else if (ex instanceof SocketException) {
			isKindOfRemoteException = true;
		}
		if (isKindOfRemoteException) {
			RemoteException rex = new RemoteException(ex.getMessage(), ex);
			return rex;
		} else {
			return ex;
		}
	}

	private RemoteInvocationType getRemoteInvocationType(Method method) {
		if (Cleanupable.class.equals(method.getDeclaringClass())) {
			return RemoteInvocationType.cleanup;
		} else {
			return RemoteInvocationType.transaction;
		}
	}

	public TerminalKey getTerminalKey() {
		TerminalKey terminalKey = new TerminalKey();
		terminalKey.setApplication(this.remoteTerminatorInfo.getApplication());
		terminalKey.setEndpoint(this.remoteTerminatorInfo.getEndpoint());
		return terminalKey;
	}

	public TerminatorInfo getRemoteTerminatorInfo() {
		return remoteTerminatorInfo;
	}

	public void setRemoteTerminatorInfo(TerminatorInfo remoteTerminatorInfo) {
		this.remoteTerminatorInfo = remoteTerminatorInfo;
	}

	public RemoteInvocationService getRemoteInvocationService() {
		return remoteInvocationService;
	}

	public void setRemoteInvocationService(RemoteInvocationService remoteInvocationService) {
		this.remoteInvocationService = remoteInvocationService;
	}

	public RemoteInvocationServiceMarshaller getRemoteServiceFactory() {
		return remoteServiceFactory;
	}

	public void setRemoteServiceFactory(RemoteInvocationServiceMarshaller remoteServiceFactory) {
		this.remoteServiceFactory = remoteServiceFactory;
	}

	@Override
	public int hashCode() {
		int hash = 13;
		if (this.remoteTerminatorInfo == null) {
			return hash;
		}
		String app = this.remoteTerminatorInfo.getApplication();
		String end = this.remoteTerminatorInfo.getEndpoint();
		hash += 17 * (app == null ? 0 : app.hashCode());
		hash += 19 * (end == null ? 0 : end.hashCode());
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		} else if (RemoteTerminator.class.isInstance(obj) == false) {
			return false;
		} else if (this.remoteTerminatorInfo == null) {
			return false;
		}

		String thisApp = this.remoteTerminatorInfo.getApplication();
		String thisEnd = this.remoteTerminatorInfo.getEndpoint();

		RemoteTerminator terminator = (RemoteTerminator) obj;
		TerminalKey that = terminator.getTerminalKey();
		String thatApp = that.getApplication();
		String thatEnd = that.getEndpoint();

		boolean appEquals = CommonUtils.equals(thisApp, thatApp);
		boolean endEquals = CommonUtils.equals(thisEnd, thatEnd);
		return appEquals && endEquals;
	}

	@Override
	public String toString() {
		String app = this.remoteTerminatorInfo == null ? null : this.remoteTerminatorInfo.getApplication();
		String end = this.remoteTerminatorInfo == null ? null : this.remoteTerminatorInfo.getEndpoint();
		return String.format("RemoteTerminator [%s/%s]", app, end);
	}

}
