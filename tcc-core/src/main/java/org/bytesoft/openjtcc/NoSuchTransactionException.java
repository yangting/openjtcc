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
package org.bytesoft.openjtcc;

public class NoSuchTransactionException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public NoSuchTransactionException() {
		super();
	}

	public NoSuchTransactionException(String message) {
		super(message);
	}

	public NoSuchTransactionException(String message, Throwable cause) {
		super(message, cause);
	}

	public NoSuchTransactionException(Throwable cause) {
		super(cause);
	}

}
