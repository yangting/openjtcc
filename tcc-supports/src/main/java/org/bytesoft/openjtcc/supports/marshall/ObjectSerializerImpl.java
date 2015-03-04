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
package org.bytesoft.openjtcc.supports.marshall;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;

import org.bytesoft.openjtcc.supports.serialize.ObjectSerializer;

import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;

public class ObjectSerializerImpl implements ObjectSerializer {

	@Override
	public byte[] serialize(Object var) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			HessianOutput output = new HessianOutput(baos);
			output.writeObject(var);
		} catch (IOException ex) {
			throw ex;
		} finally {
			try {
				baos.close();
			} catch (Exception ex) {
			}
		}
		return baos.toByteArray();
	}

	@Override
	public Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
		Serializable variable = null;
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		try {
			HessianInput input = new HessianInput(bais);
			variable = (Serializable) input.readObject();
		} catch (IOException ex) {
			throw ex;
		} finally {
			try {
				bais.close();
			} catch (Exception ex) {
			}
		}
		return variable;
	}

}
