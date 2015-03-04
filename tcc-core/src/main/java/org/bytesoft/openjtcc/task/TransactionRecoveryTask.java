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
package org.bytesoft.openjtcc.task;

import org.bytesoft.openjtcc.recovery.RecoveryManager;

public class TransactionRecoveryTask extends AbstractScheduleTask {
	private static final int MIN_INTERVAL_SECONDS = 5;
	private static final int MAX_INTERVAL_SECONDS = 1800;
	private static final int DEFAULT_INTERVAL_SECONDS = 30;

	private int expireIntervalSeconds = DEFAULT_INTERVAL_SECONDS;
	private RecoveryManager recoveryManager;

	@Override
	public void execute() {
		this.recoveryManager.recover();
	}

	@Override
	public int getIntervalSeconds() {
		int seconds = DEFAULT_INTERVAL_SECONDS;
		if (this.expireIntervalSeconds < MIN_INTERVAL_SECONDS || this.expireIntervalSeconds > MAX_INTERVAL_SECONDS) {
			seconds = DEFAULT_INTERVAL_SECONDS;
		} else {
			seconds = this.expireIntervalSeconds;
		}
		return seconds;
	}

	public RecoveryManager getRecoveryManager() {
		return recoveryManager;
	}

	public void setRecoveryManager(RecoveryManager recoveryManager) {
		this.recoveryManager = recoveryManager;
	}

	public int getExpireIntervalSeconds() {
		return expireIntervalSeconds;
	}

	public void setExpireIntervalSeconds(int expireIntervalSeconds) {
		this.expireIntervalSeconds = expireIntervalSeconds;
	}
}
