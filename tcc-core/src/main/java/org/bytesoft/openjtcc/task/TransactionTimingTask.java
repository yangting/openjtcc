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

import org.bytesoft.openjtcc.supports.schedule.TimingProcesser;

public class TransactionTimingTask extends AbstractScheduleTask {
	private static final int MIN_INTERVAL_SECONDS = 1;
	private static final int MAX_INTERVAL_SECONDS = 1800;
	private static final int DEFAULT_INTERVAL_SECONDS = 1;

	private int timingIntervalSeconds = DEFAULT_INTERVAL_SECONDS;
	private TimingProcesser timingProcesser;

	@Override
	public void execute() {
		this.timingProcesser.processTimingTransaction();
		this.timingProcesser.processExpireTransaction();
	}

	@Override
	public int getIntervalSeconds() {
		int seconds = DEFAULT_INTERVAL_SECONDS;
		if (this.timingIntervalSeconds < MIN_INTERVAL_SECONDS || this.timingIntervalSeconds > MAX_INTERVAL_SECONDS) {
			seconds = DEFAULT_INTERVAL_SECONDS;
		} else {
			seconds = this.timingIntervalSeconds;
		}
		return seconds;
	}

	public TimingProcesser getTimingProcesser() {
		return timingProcesser;
	}

	public void setTimingProcesser(TimingProcesser timingProcesser) {
		this.timingProcesser = timingProcesser;
	}

	public int getTimingIntervalSeconds() {
		return timingIntervalSeconds;
	}

	public void setTimingIntervalSeconds(int timingIntervalSeconds) {
		this.timingIntervalSeconds = timingIntervalSeconds;
	}

}
