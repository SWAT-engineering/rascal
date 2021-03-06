/*******************************************************************************
 * Copyright (c) 2012-2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *   * Michael Steindorfer - Michael.Steindorfer@cwi.nl - CWI
*******************************************************************************/
package org.rascalmpl.debug;

/**
 * Interface to mark an class as an event trigger, where you can register for as
 * a listener.
 */
public interface IRascalEventTrigger {

	void addRascalEventListener(IRascalEventListener listener);
	void removeRascalEventListener(IRascalEventListener listener);
	
}
