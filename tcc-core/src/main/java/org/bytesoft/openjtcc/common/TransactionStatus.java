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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.transaction.Status;

public final class TransactionStatus implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger("openjtcc");

	public static final int STATUS_ACTIVE = 0x0;
	public static final int STATUS_MARKED_ROLLBACK = 0x1;

	public static final int STATUS_PREPARING = 0x2;
	public static final int STATUS_PREPARE_FAIL = 0x4;
	public static final int STATUS_PREPARED = 0x8;

	public static final int STATUS_COMMITTING = 0x10;// 16
	public static final int STATUS_COMMIT_FAIL = 0x20;// 32
	public static final int STATUS_COMMITTED = 0x40;// 64

	public static final int STATUS_ROLLINGBACK = 0x80;// 128
	public static final int STATUS_ROLLBACK_FAIL = 0x100;// 256
	public static final int STATUS_ROLLEDBACK = 0x200;// 512

	public static final int STATUS_UNKNOWN = 0x400;// 1024

	protected int innerStatus = STATUS_ACTIVE;
	protected int innerStatusTrace = STATUS_ACTIVE;
	private int transactionStatus = Status.STATUS_ACTIVE;

	public TransactionStatus() {
	}

	public TransactionStatus(int status, int statusTrace) {
		this.initTransactionStatus(status, statusTrace);
	}

	public void initTransactionStatus(int status, int statusTrace) {
		this.innerStatus = status;
		this.innerStatusTrace = statusTrace;
		switch (status) {
		case STATUS_ACTIVE:
			this.transactionStatus = Status.STATUS_ACTIVE;
			break;
		case STATUS_MARKED_ROLLBACK:
			this.transactionStatus = Status.STATUS_MARKED_ROLLBACK;
			break;
		case STATUS_PREPARING:
		case STATUS_PREPARE_FAIL:
			this.transactionStatus = Status.STATUS_PREPARING;
			break;
		case STATUS_PREPARED:
			this.transactionStatus = Status.STATUS_PREPARED;
			break;
		case STATUS_COMMITTING:
		case STATUS_COMMIT_FAIL:
			this.transactionStatus = Status.STATUS_COMMITTING;
			break;
		case STATUS_COMMITTED:
			this.transactionStatus = Status.STATUS_COMMITTED;
			break;

		case STATUS_ROLLINGBACK:
		case STATUS_ROLLBACK_FAIL:
			this.transactionStatus = Status.STATUS_ROLLING_BACK;
			break;
		case STATUS_ROLLEDBACK:
			this.transactionStatus = Status.STATUS_ROLLEDBACK;
			break;
		case STATUS_UNKNOWN:
			this.transactionStatus = Status.STATUS_UNKNOWN;
			break;
		}
	}

	public boolean isMarkedRollbackOnly() {
		return this.transactionStatus == Status.STATUS_MARKED_ROLLBACK;
	}

	public boolean isActive() {
		return this.transactionStatus == Status.STATUS_ACTIVE;
	}

	public boolean isPreparing() {
		return this.transactionStatus == Status.STATUS_PREPARING;
	}

	public boolean isPrepareFail() {
		boolean preparing = this.isPreparing();
		boolean failure = this.innerStatus == TransactionStatus.STATUS_PREPARE_FAIL;
		return preparing && failure;
	}

	public boolean isPrepared() {
		return this.transactionStatus == Status.STATUS_PREPARED;
	}

	public boolean isCommitting() {
		return this.transactionStatus == Status.STATUS_COMMITTING;
	}

	public boolean isCommitFail() {
		boolean committing = this.isCommitting();
		boolean failure = this.innerStatus == TransactionStatus.STATUS_COMMIT_FAIL;
		return committing && failure;
	}

	public boolean isCommitted() {
		return this.transactionStatus == Status.STATUS_COMMITTED;
	}

	public boolean isRollingBack() {
		return this.transactionStatus == Status.STATUS_ROLLING_BACK;
	}

	public boolean isRollbackFail() {
		boolean rolling = this.isRollingBack();
		boolean failure = this.innerStatus == TransactionStatus.STATUS_ROLLBACK_FAIL;
		return rolling && failure;
	}

	public boolean isRolledBack() {
		return this.transactionStatus == Status.STATUS_ROLLEDBACK;
	}

	public void markStatusRollback() throws IllegalStateException {
		if (this.transactionStatus == Status.STATUS_ACTIVE) {
			String src = this.getStatusCode();
			this.transactionStatus = Status.STATUS_MARKED_ROLLBACK;
			String dst = this.getStatusCode();
			logger.log(Level.FINEST, String.format("[status] %22s -> %22s ", src, dst));
		} else if (this.transactionStatus == Status.STATUS_MARKED_ROLLBACK) {
			// do nothing
		} else {
			throw new IllegalStateException();
		}
	}

	public int getTransactionStatus() {
		return this.transactionStatus;
	}

	public int getInnerStatus() {
		return this.innerStatus;
	}

	public int getInnerStatusTrace() {
		return this.innerStatusTrace;
	}

	public void setStatusActive() throws IllegalStateException {
		String src = this.getStatusCode();
		this.transactionStatus = Status.STATUS_ACTIVE;
		this.innerStatus = STATUS_ACTIVE;
		this.innerStatusTrace |= STATUS_ACTIVE;

		String dst = this.getStatusCode();
		logger.log(Level.FINEST, String.format("[status] %22s -> %22s ", src, dst));
	}

	public void setStatusPreparing() throws IllegalStateException {
		String src = this.getStatusCode();
		if (this.transactionStatus == Status.STATUS_ACTIVE //
				|| this.transactionStatus == Status.STATUS_MARKED_ROLLBACK//
				|| this.transactionStatus == Status.STATUS_PREPARING//
				|| this.transactionStatus == Status.STATUS_UNKNOWN) {

			this.transactionStatus = Status.STATUS_PREPARING;
			this.innerStatus = STATUS_PREPARING;
			this.innerStatusTrace |= STATUS_PREPARING;
			String dst = this.getStatusCode();
			logger.log(Level.FINEST, String.format("[status] %22s -> %22s ", src, dst));
		} else {
			String dst = this.getStatusCode(Status.STATUS_PREPARING);
			throw new IllegalStateException(String.format("[status] %22s -> %22s ", src, dst));
		}
	}

	public void setStatusPrepareFail() throws IllegalStateException {
		if (this.transactionStatus == Status.STATUS_PREPARING //
				&& this.innerStatus == STATUS_PREPARING) {
			this.innerStatus = STATUS_PREPARE_FAIL;
			this.innerStatusTrace |= STATUS_PREPARE_FAIL;
		} else {
			throw new IllegalStateException();
		}
	}

	public void setStatusPrepared() throws IllegalStateException {
		String src = this.getStatusCode();
		if (this.transactionStatus == Status.STATUS_ACTIVE //
				|| this.transactionStatus == Status.STATUS_MARKED_ROLLBACK//
				|| this.transactionStatus == Status.STATUS_PREPARING//
				|| this.transactionStatus == Status.STATUS_PREPARED//
				|| this.transactionStatus == Status.STATUS_UNKNOWN) {
			this.transactionStatus = Status.STATUS_PREPARED;
			this.innerStatus = STATUS_PREPARED;
			this.innerStatusTrace |= STATUS_PREPARED;
			String dst = this.getStatusCode();
			logger.log(Level.FINEST, String.format("[status] %22s -> %22s ", src, dst));
		} else {
			String dst = this.getStatusCode(Status.STATUS_PREPARED);
			throw new IllegalStateException(String.format("[status] %22s -> %22s ", src, dst));
		}
	}

	public void changeStatusToCommiting() throws IllegalStateException {
		String src = this.getStatusCode();
		if (this.transactionStatus == Status.STATUS_ACTIVE //
				|| this.transactionStatus == Status.STATUS_PREPARING//
				|| this.transactionStatus == Status.STATUS_PREPARED//
				|| this.transactionStatus == Status.STATUS_COMMITTING//
				|| this.transactionStatus == Status.STATUS_ROLLING_BACK//
				|| this.transactionStatus == Status.STATUS_UNKNOWN) {
			this.transactionStatus = Status.STATUS_COMMITTING;
			this.innerStatus = STATUS_COMMITTING;
			this.innerStatusTrace |= STATUS_COMMITTING;
			String dst = this.getStatusCode();
			logger.log(Level.FINEST, String.format("[status] %22s -> %22s ", src, dst));
		} else {
			String dst = this.getStatusCode(Status.STATUS_COMMITTING);
			throw new IllegalStateException(String.format("[status] %22s -> %22s ", src, dst));
		}
	}

	public void setStatusCommiting() throws IllegalStateException {
		String src = this.getStatusCode();
		if (this.transactionStatus == Status.STATUS_ACTIVE //
				|| this.transactionStatus == Status.STATUS_PREPARING//
				|| this.transactionStatus == Status.STATUS_PREPARED//
				|| this.transactionStatus == Status.STATUS_COMMITTING//
				|| this.transactionStatus == Status.STATUS_UNKNOWN) {
			this.transactionStatus = Status.STATUS_COMMITTING;
			this.innerStatus = STATUS_COMMITTING;
			this.innerStatusTrace |= STATUS_COMMITTING;
			String dst = this.getStatusCode();
			logger.log(Level.FINEST, String.format("[status] %22s -> %22s ", src, dst));
		} else {
			String dst = this.getStatusCode(Status.STATUS_COMMITTING);
			throw new IllegalStateException(String.format("[status] %22s -> %22s ", src, dst));
		}
	}

	public void setStatusCommitFail() throws IllegalStateException {
		if (this.transactionStatus == Status.STATUS_COMMITTING //
				&& this.innerStatus == STATUS_COMMITTING) {
			this.innerStatus = STATUS_COMMITTING;
			this.innerStatusTrace |= STATUS_COMMITTING;
		} else {
			throw new IllegalStateException();
		}
	}

	public void setStatusCommitted() throws IllegalStateException {
		String src = this.getStatusCode();
		if (this.transactionStatus == Status.STATUS_COMMITTING//
				|| this.transactionStatus == Status.STATUS_COMMITTED//
				|| this.transactionStatus == Status.STATUS_UNKNOWN) {
			this.transactionStatus = Status.STATUS_COMMITTED;
			this.innerStatus = STATUS_COMMITTED;
			this.innerStatusTrace |= STATUS_COMMITTED;
			String dst = this.getStatusCode();
			logger.log(Level.FINEST, String.format("[status] %22s -> %22s ", src, dst));
		} else {
			String dst = this.getStatusCode(Status.STATUS_COMMITTED);
			throw new IllegalStateException(String.format("[status] %22s -> %22s ", src, dst));
		}
	}

	public void setStatusRollingback() throws IllegalStateException {
		String src = this.getStatusCode();
		if (this.transactionStatus == Status.STATUS_ACTIVE //
				|| this.transactionStatus == Status.STATUS_MARKED_ROLLBACK//
				|| this.transactionStatus == Status.STATUS_PREPARING//
				|| this.transactionStatus == Status.STATUS_PREPARED//
				|| this.transactionStatus == Status.STATUS_ROLLING_BACK//
				|| this.transactionStatus == Status.STATUS_COMMITTING//
				|| this.transactionStatus == Status.STATUS_UNKNOWN) {
			this.transactionStatus = Status.STATUS_ROLLING_BACK;
			this.innerStatus = STATUS_ROLLINGBACK;
			this.innerStatusTrace |= STATUS_ROLLINGBACK;
			String dst = this.getStatusCode();
			logger.log(Level.FINEST, String.format("[status] %22s -> %22s ", src, dst));
		} else {
			String dst = this.getStatusCode(Status.STATUS_ROLLING_BACK);
			throw new IllegalStateException(String.format("[status] %22s -> %22s ", src, dst));
		}
	}

	public void setStatusRollbackFail() throws IllegalStateException {
		if (this.transactionStatus == Status.STATUS_ROLLING_BACK //
				&& this.innerStatus == STATUS_ROLLINGBACK) {
			this.innerStatus = STATUS_ROLLINGBACK;
			this.innerStatusTrace |= STATUS_ROLLINGBACK;
		} else {
			throw new IllegalStateException();
		}
	}

	public void setStatusRolledback() throws IllegalStateException {
		String src = this.getStatusCode();
		if (this.transactionStatus == Status.STATUS_ROLLING_BACK//
				|| this.transactionStatus == Status.STATUS_ROLLEDBACK//
				|| this.transactionStatus == Status.STATUS_UNKNOWN) {
			this.transactionStatus = Status.STATUS_ROLLEDBACK;
			this.innerStatus = STATUS_ROLLEDBACK;
			this.innerStatusTrace |= STATUS_ROLLEDBACK;
			String dst = this.getStatusCode();
			logger.log(Level.FINEST, String.format("[status] %22s -> %22s ", src, dst));
		} else {
			String dst = this.getStatusCode(Status.STATUS_ROLLEDBACK);
			throw new IllegalStateException(String.format("[status] %22s -> %22s ", src, dst));
		}
	}

	public void setStatusUnknown() throws IllegalStateException {
		String src = this.getStatusCode();
		this.transactionStatus = Status.STATUS_UNKNOWN;
		this.innerStatus = STATUS_UNKNOWN;
		this.innerStatusTrace |= STATUS_UNKNOWN;
		String dst = this.getStatusCode();
		logger.log(Level.FINEST, String.format("[status] %22s -> %22s ", src, dst));
	}

	public String getStatusCode() {
		return this.getStatusCode(this.transactionStatus);
	}

	public String getStatusCode(int status) {
		if (Status.STATUS_ACTIVE == status) {
			return "STATUS_ACTIVE";
		} else if (Status.STATUS_COMMITTED == status) {
			return "STATUS_COMMITTED";
		} else if (Status.STATUS_COMMITTING == status) {
			return "STATUS_COMMITTING";
		} else if (Status.STATUS_MARKED_ROLLBACK == status) {
			return "STATUS_MARKED_ROLLBACK";
		} else if (Status.STATUS_NO_TRANSACTION == status) {
			return "STATUS_NO_TRANSACTION";
		} else if (Status.STATUS_PREPARED == status) {
			return "STATUS_PREPARED";
		} else if (Status.STATUS_PREPARING == status) {
			return "STATUS_PREPARING";
		} else if (Status.STATUS_ROLLEDBACK == status) {
			return "STATUS_ROLLEDBACK";
		} else if (Status.STATUS_ROLLING_BACK == status) {
			return "STATUS_ROLLING_BACK";
		} else if (Status.STATUS_UNKNOWN == status) {
			return "STATUS_UNKNOWN";
		}
		return "STATUS_UNKNOWN";
	}
}
