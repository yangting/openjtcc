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
package org.bytesoft.openjtcc.internal;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

import org.bytesoft.openjtcc.archive.XAResourceArchive;
import org.bytesoft.openjtcc.common.TransactionContext;
import org.bytesoft.openjtcc.xa.XidFactory;
import org.bytesoft.openjtcc.xa.XidImpl;

public class JtaResourceCoordinator {
	private static final Logger logger = Logger.getLogger("openjtcc");

	private transient final Set<XAResourceArchive> enlistedXAResources = new HashSet<XAResourceArchive>();
	private transient final Set<XAResourceArchive> delistedXAResources = new HashSet<XAResourceArchive>();
	private transient final Set<XAResourceArchive> participantXAResources = new HashSet<XAResourceArchive>();
	private transient final Set<XAResourceArchive> suspendedXAResources = new HashSet<XAResourceArchive>();

	private XidFactory xidFactory;
	private TransactionContext transactionContext;

	public synchronized boolean delistResource(XAResource xares, int flags) throws IllegalStateException,
			SystemException {
		XAResourceArchive holder = null;
		for (XAResourceArchive existedHolder : this.participantXAResources) {
			boolean isSameRM = false;
			if (existedHolder.xaRes.equals(xares)) {
				isSameRM = true;
			} else {
				try {
					isSameRM = existedHolder.xaRes.isSameRM(xares);
				} catch (XAException ex) {
					ex.printStackTrace();
				} catch (RuntimeException ex) {
					ex.printStackTrace();
				}
			}
			if (isSameRM) {
				holder = existedHolder;
				break;
			}
		}

		if (holder == null) {
			return false;
		} else {
			return this.delistResource(holder, flags);
		}
	}

	public synchronized boolean enlistResource(XAResource xares) throws IllegalStateException, RollbackException,
			SystemException {
		XAResourceArchive holder = null;
		for (XAResourceArchive existedHolder : this.participantXAResources) {
			boolean isSameRM = false;
			if (existedHolder.xaRes.equals(xares)) {
				isSameRM = true;
			} else {
				try {
					isSameRM = existedHolder.xaRes.isSameRM(xares);
				} catch (XAException e) {
				} catch (RuntimeException ex) {
				}
			}

			if (isSameRM) {
				holder = existedHolder;
				break;
			}
		}
		int flags = XAResource.TMNOFLAGS;
		if (holder == null) {
			holder = new XAResourceArchive();
			holder.xaRes = xares;
			XidImpl globalXid = this.transactionContext.getGlobalXid();
			holder.xid = this.xidFactory.createBranchXid(globalXid);
		} else {
			flags = XAResource.TMJOIN;
		}
		return this.enlistResource(holder, flags);
	}

	private boolean enlistResource(final XAResourceArchive holder, final int flags) throws SystemException,
			RollbackException {
		logger.info(String.format("\t[jta] enlist: xares= %s", holder.xaRes));
		try {
			holder.xaRes.start(holder.xid, flags);
			if (flags == XAResource.TMNOFLAGS) {
				this.participantXAResources.add(holder);
				this.enlistedXAResources.add(holder);
			} else if (flags == XAResource.TMJOIN) {
				if (this.enlistedXAResources.contains(holder)) {
					// do nothing
				} else if (this.delistedXAResources.contains(holder)) {
					this.delistedXAResources.remove(holder);
					this.enlistedXAResources.add(holder);
				} else {
					throw new SystemException();
				}
			} else {
				throw new SystemException();
			}
			return true;
		} catch (XAException xae) {
			switch (xae.errorCode) {
			case XAException.XAER_RMFAIL:
			case XAException.XAER_DUPID:
			case XAException.XAER_OUTSIDE:
			case XAException.XAER_NOTA:
			case XAException.XAER_INVAL:
			case XAException.XAER_PROTO:
				return false;
			case XAException.XAER_RMERR:
				SystemException sysex = new SystemException();
				sysex.initCause(xae);
				throw sysex;
			default:
				throw new RollbackException();
			}

		} catch (RuntimeException ex) {
			// throw ex;
			throw new RollbackException();
		}
	}

	private synchronized boolean delistResource(XAResourceArchive holder, int flags) throws SystemException {
		logger.info(String.format("\t[jta] delist: xares= %s", holder.xaRes));
		try {
			holder.xaRes.end(holder.xid, flags);
			if (this.enlistedXAResources.contains(holder)) {
				this.delistedXAResources.add(holder);
				this.enlistedXAResources.remove(holder);
			} else if (this.delistedXAResources.contains(holder)) {
				// do nothing
			} else {
				throw new SystemException();
			}
			return true;
		} catch (XAException xae) {

			switch (xae.errorCode) {
			case XAException.XAER_RMFAIL:
			case XAException.XAER_NOTA:
			case XAException.XAER_INVAL:
			case XAException.XAER_PROTO:
				return false;
			case XAException.XAER_RMERR:
			default:
				SystemException sysex = new SystemException();
				sysex.initCause(xae);
				throw sysex;
			}

		} catch (RuntimeException ex) {
			throw ex;
		}
	}

	public synchronized void delistParticipantXAResources() {
		Set<XAResourceArchive> holders = new HashSet<XAResourceArchive>(this.enlistedXAResources);
		Iterator<XAResourceArchive> enlistedItr = holders.iterator();
		while (enlistedItr.hasNext()) {
			XAResourceArchive holder = enlistedItr.next();
			try {
				this.delistResource(holder, XAResource.TMSUCCESS);
			} catch (SystemException ex) {
				// TODO ignore
				ex.printStackTrace();
			} catch (RuntimeException ex) {
				// TODO ignore
				ex.printStackTrace();
			}
		}
	}

	public synchronized void resumeAllResource() throws IllegalStateException, SystemException {
		boolean errorExists = false;
		// boolean rollbackRequired = false;
		Iterator<XAResourceArchive> itr = this.suspendedXAResources.iterator();
		while (itr.hasNext()) {
			XAResourceArchive holder = itr.next();
			this.enlistedXAResources.add(holder);
			itr.remove();
			try {
				holder.xaRes.start(holder.xid, XAResource.TMRESUME);
			} catch (XAException xae) {
				xae.printStackTrace();

				switch (xae.errorCode) {
				case XAException.XAER_RMFAIL:
				case XAException.XAER_DUPID:
				case XAException.XAER_OUTSIDE:
				case XAException.XAER_NOTA:
				case XAException.XAER_INVAL:
				case XAException.XAER_PROTO:
					break;
				case XAException.XAER_RMERR:

					errorExists = true;
					break;
				default:
					// rollbackRequired = true;
				}
			} catch (RuntimeException ex) {
				// TODO
				ex.printStackTrace();
			}
		}

		/* if (rollbackRequired) { throw new RollbackRequiredException(); } else */
		if (errorExists) {
			throw new SystemException();
		}

	}

	public synchronized void suspendAllResource() throws IllegalStateException, SystemException {
		// boolean rollbackRequired = false;
		boolean errorExists = false;
		Iterator<XAResourceArchive> itr = this.enlistedXAResources.iterator();
		while (itr.hasNext()) {
			XAResourceArchive holder = itr.next();
			this.suspendedXAResources.add(holder);
			itr.remove();
			try {
				holder.xaRes.end(holder.xid, XAResource.TMSUSPEND);
			} catch (XAException xae) {
				xae.printStackTrace();
				switch (xae.errorCode) {
				case XAException.XAER_RMFAIL:
				case XAException.XAER_NOTA:
				case XAException.XAER_INVAL:
				case XAException.XAER_PROTO:
					break;
				case XAException.XAER_RMERR:
					errorExists = true;
					break;
				default:
					// rollbackRequired = true;
				}
			} catch (RuntimeException ex) {
				// TODO
				ex.printStackTrace();
			}
		}

		/* if (rollbackRequired) { throw new RollbackRequiredException(); } else */
		if (errorExists) {
			throw new SystemException();
		}
	}

	public boolean xaPrepare() {
		boolean rollback = false;
		Iterator<XAResourceArchive> delistedItr = this.delistedXAResources.iterator();
		while (delistedItr.hasNext()) {
			XAResourceArchive holder = delistedItr.next();
			try {
				holder.vote = holder.xaRes.prepare(holder.xid);
				if (holder.vote == XAResource.XA_RDONLY) {
					holder.completed = true;
				}
			} catch (XAException ex) {
				rollback = true;
			} catch (RuntimeException e) {
				rollback = true;
			}
		}
		return rollback;
	}

	public void xaCommit() throws HeuristicMixedException, HeuristicRollbackException, RollbackException,
			SecurityException, SystemException {
		boolean errorExists = false;
		boolean rollbackExists = false;
		boolean commitExists = false;
		boolean mixedExists = false;
		Iterator<XAResourceArchive> itr = this.delistedXAResources.iterator();
		while (itr.hasNext()) {
			XAResourceArchive holder = itr.next();
			logger.info(String.format("\t[jta] commit: xares= %s", holder.xaRes));
			if (holder.completed) {
				if (holder.rolledback) {
					errorExists = true;
					rollbackExists = true;
				} else if (holder.committed) {
					commitExists = true;
				}
			} else {
				try {
					holder.xaRes.commit(holder.xid, false);
					holder.completed = true;
					holder.committed = true;
					commitExists = true;
				} catch (XAException xae) {
					switch (xae.errorCode) {
					case XAException.XA_HEURHAZ:
					case XAException.XA_HEURMIX:
						mixedExists = true;
						errorExists = true;
						break;
					case XAException.XA_HEURCOM:
						commitExists = true;
						break;
					case XAException.XAER_RMERR:
					case XAException.XAER_RMFAIL:
						errorExists = true;
						break;
					case XAException.XAER_NOTA:
					case XAException.XAER_INVAL:
					case XAException.XAER_PROTO:
						// ignore
						break;
					case XAException.XA_HEURRB:
					default:
						rollbackExists = true;
					}
				} catch (RuntimeException ex) {
					errorExists = true;
				}
			}
		}

		if (mixedExists) {
			throw new HeuristicMixedException();
		} else if (errorExists) {
			if (rollbackExists) {
				if (commitExists) {
					throw new HeuristicMixedException();
				} else {
					throw new SystemException();
				}
			} else {
				throw new SystemException();
			}
		}

	}

	public void xaRollback() throws IllegalStateException, SystemException {
		boolean errorExists = false;
		// boolean commitExists = false;
		// boolean rollbackExists = false;
		// boolean mixedExists = false;
		Iterator<XAResourceArchive> delistedItr = this.delistedXAResources.iterator();
		while (delistedItr.hasNext()) {
			XAResourceArchive holder = delistedItr.next();
			logger.info(String.format("\t[jta] rollback: xares= %s", holder.xaRes));
			if (holder.completed) {
				if (holder.committed) {
					errorExists = true;
				}
			} else {
				try {
					holder.xaRes.rollback(holder.xid);
					holder.completed = true;
					holder.rolledback = true;
				} catch (XAException xae) {

					switch (xae.errorCode) {
					case XAException.XA_HEURMIX:
					case XAException.XA_HEURHAZ:
						errorExists = true;
						// mixedExists = true;
						break;
					case XAException.XA_HEURCOM:
						// commitExists = true;
						errorExists = true;
						break;
					case XAException.XAER_NOTA:
					case XAException.XAER_INVAL:
					case XAException.XAER_PROTO:
						// ignore
						break;
					case XAException.XAER_RMERR:
					case XAException.XAER_RMFAIL:
						errorExists = true;
					case XAException.XA_HEURRB:
						// rollbackExists = true;
						break;
					default:
						// ignore
					}

				} catch (RuntimeException ex) {
					errorExists = true;
				}
			}
		}

		if (errorExists) {
			throw new SystemException();
		}
	}

	public XidFactory getXidFactory() {
		return xidFactory;
	}

	public void setXidFactory(XidFactory xidFactory) {
		this.xidFactory = xidFactory;
	}

	public TransactionContext getTransactionContext() {
		return transactionContext;
	}

	public void setTransactionContext(TransactionContext transactionContext) {
		this.transactionContext = transactionContext;
	}

}
