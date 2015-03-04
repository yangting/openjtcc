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

import javax.transaction.TransactionManager;

import org.bytesoft.openjtcc.TransactionImpl;
import org.bytesoft.openjtcc.TransactionManagerImpl;
import org.bytesoft.openjtcc.common.TerminalKey;
import org.bytesoft.openjtcc.common.TransactionContext;
import org.bytesoft.openjtcc.supports.dubbo.RemoteInvocationService;
import org.bytesoft.openjtcc.supports.dubbo.RemoteInvocationServiceFactory;
import org.bytesoft.openjtcc.supports.dubbo.RemoteInvocationType;
import org.bytesoft.openjtcc.supports.rmi.RemoteInvocationInterceptor;

public class RemoteInvocationClientInvoker implements InvocationHandler {
	private String beanId;
	private TerminalKey terminalKey;
	private RemoteInvocationServiceFactory remoteInvocationServiceFactory;
	private RemoteInvocationInterceptor remoteInvocationInterceptor;
	private TransactionManager transactionManager;
	private Class<?> remoteInterfaceClass;

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (Object.class.equals(method.getDeclaringClass())) {
			return method.invoke(this, args);
		}

		boolean methodLocated = false;
		String methodName = method.getName();
		Class<?>[] types = method.getParameterTypes();
		try {
			Method meth = this.getClass().getDeclaredMethod(methodName, types);
			methodLocated = true;
			return meth.invoke(this, args);
		} catch (NoSuchMethodException ex) {
			// ignore
		} catch (SecurityException ex) {
			// ignore
		} catch (IllegalAccessException ex) {
			RuntimeException rex = new RuntimeException();
			rex.initCause(ex);
			throw rex;
		} catch (IllegalArgumentException ex) {
			throw ex;
		} catch (InvocationTargetException ex) {
			throw ex.getTargetException();
		} catch (RuntimeException ex) {
			if (methodLocated) {
				throw ex;
			} else {
				// ignore
			}
		}

		return this.processRemoteInvocation(method, args);
	}

	private Object processRemoteInvocation(Method method, Object[] args) throws Throwable {
		Class<?> declaringClass = method.getDeclaringClass();
		String methodName = method.getName();
		RemoteInvocationRequestImpl request = new RemoteInvocationRequestImpl();
		request.setBeanId(this.beanId);
		request.setInvocationType(RemoteInvocationType.service);
		request.setDeclaringClass(declaringClass.getName());
		request.setMethodName(methodName);
		Class<?>[] types = method.getParameterTypes();
		String[] clzes = new String[types.length];
		for (int i = 0; i < types.length; i++) {
			clzes[i] = types[i].getName();
		}
		request.setParameterTypes(clzes);
		request.setParameterValues(args);
		request.setInterfaceClass(this.remoteInterfaceClass.getName());

		RemoteInvocationResponseImpl response = null;
		try {
			TransactionManagerImpl txManager = (TransactionManagerImpl) this.transactionManager;
			TransactionImpl transaction = txManager.getCurrentTransaction();
			TerminalKey terminalKey = null;
			if (transaction != null) {
				terminalKey = transaction.getTerminalKey(this.terminalKey.getApplication());
			}

			if (terminalKey == null) {
				terminalKey = this.terminalKey;
				request.setTerminalKey(terminalKey);
			} else {
				request.setTerminalKey(terminalKey);
			}

			this.beforeSendRequest(request);

			RemoteInvocationService remoteService = this.remoteInvocationServiceFactory
					.getRemoteInvocationService(terminalKey);
			response = remoteService.invoke(request);
			response.setRequest(request);
		} catch (Throwable ex) {
			response = new RemoteInvocationResponseImpl();
			response.setRequest(request);
			response.setInvocationType(request.getInvocationType());

			Throwable thrown = this.getFinalResponseThrowable(ex);
			response.setThrowable(thrown);
			throw response.getThrowable();
		} finally {
			try {
				this.afterReceiveResponse(response);
			} catch (RuntimeException rex) {
				if (response.isFailure()) {
					// ignore
				} else {
					response.setThrowable(rex);
				}
			}

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

	private void afterReceiveResponse(RemoteInvocationResponseImpl response) {
		TransactionManagerImpl txManager = (TransactionManagerImpl) this.transactionManager;
		TransactionImpl transaction = txManager.getCurrentTransaction();
		if (transaction != null) {
			TransactionContext context = transaction.getTransactionContext();
			if (context.isCompensable() && this.remoteInvocationInterceptor != null) {
				TerminalKey terminalKey = response.getTerminalKey();
				TransactionContext propagationContext = (TransactionContext) response.getTransactionContext();
				propagationContext.setTerminalKey(terminalKey);
				this.remoteInvocationInterceptor.afterReceiveResponse(response);
			}
		}
	}

	private void beforeSendRequest(RemoteInvocationRequestImpl request) {
		TransactionManagerImpl txManager = (TransactionManagerImpl) this.transactionManager;
		TransactionImpl transaction = txManager.getCurrentTransaction();
		if (transaction != null) {
			TransactionContext context = transaction.getTransactionContext();
			if (context.isCompensable() && this.remoteInvocationInterceptor != null) {
				this.remoteInvocationInterceptor.beforeSendRequest(request);
			}
		}
	}

	public void setRemoteInvocationServiceFactory(RemoteInvocationServiceFactory remoteInvocationServiceFactory) {
		this.remoteInvocationServiceFactory = remoteInvocationServiceFactory;
	}

	public void setRemoteInvocationInterceptor(RemoteInvocationInterceptor remoteInvocationInterceptor) {
		this.remoteInvocationInterceptor = remoteInvocationInterceptor;
	}

	public TransactionManager getTransactionManager() {
		return transactionManager;
	}

	public void setTransactionManager(TransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	public Class<?> getRemoteInterfaceClass() {
		return remoteInterfaceClass;
	}

	public void setRemoteInterfaceClass(Class<?> remoteInterfaceClass) {
		this.remoteInterfaceClass = remoteInterfaceClass;
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

}
