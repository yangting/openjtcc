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

public abstract class AbstractScheduleTask implements Runnable {
	public static final long SECOND_MILLIS = 1000L;
	public static final long SLEEP_UNIT_MILLIS = 100L;

	private boolean released;

	public void run() {
		int seconds = this.getIntervalSeconds();
		long timingInterval = SECOND_MILLIS * seconds;
		this.process(timingInterval);
	}

	private void process(long interval) {
		long nextTimingMillis = System.currentTimeMillis();// + interval;
		while (this.isActive()) {
			long currentMillis = System.currentTimeMillis();
			if (currentMillis >= nextTimingMillis) {
				try {
					this.execute();
				} catch (Exception ex) {
					// ignore
				}
				long now = System.currentTimeMillis();
				long costMillis = now - currentMillis;

				if (costMillis > interval) {
					long millis = costMillis / interval;
					nextTimingMillis = now + (interval - millis);
				} else {
					nextTimingMillis = currentMillis + interval;
				}
			} else {
				this.sleepMillis(nextTimingMillis - currentMillis);
			}
		}// end-while
	}

	public void sleepMillis(long millis) {
		if (millis > SLEEP_UNIT_MILLIS) {
			long begin = System.currentTimeMillis();
			long remainMillis = millis;
			do {
				this.handleDefaultSleep();
				remainMillis = remainMillis - SLEEP_UNIT_MILLIS;
			} while (this.isActive() && remainMillis > SLEEP_UNIT_MILLIS);
			if (this.isActive()) {
				long now = System.currentTimeMillis();
				long past = now - begin;
				if (millis > past) {
					this.handleSleepMillis(millis - past);
				}
			}
		} else {
			this.handleSleepMillis(millis);
		}
	}

	private void handleSleepMillis(long x) {
		if (this.isActive()) {
			try {
				Thread.sleep(x);
			} catch (InterruptedException e) {
				// ignore
			}
		}
	}

	private void handleDefaultSleep() {
		if (this.isActive()) {
			try {
				Thread.sleep(SLEEP_UNIT_MILLIS);
			} catch (InterruptedException e) {
				// ignore
			}
		}
	}

	/**
	 * This method would be called on a separate thread than the one currently executing Runnable.run(). Since this
	 * method call causes the current instance to be simultaneously acted upon by multiple threads, the current instance
	 * must be thread-safe, and this method must be re-entrant.
	 */
	public void release() {
		this.released = true;
	}

	public abstract int getIntervalSeconds();

	public abstract void execute();

	public boolean isActive() {
		return this.released == false;
	}
}
