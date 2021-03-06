/**
 * $RCSfile$
 * $Revision$
 * $Date$
 * <p>
 * Copyright 2003-2007 Jive Software.
 * <p>
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smackx.favoritedme;

import org.jivesoftware.smack.packet.IQ;

import java.util.ArrayList;

/**
 * Represents XMPP roster packets.
 *
 * @author Matt Tucker
 */
public class FavoritedMePacket extends IQ {

    private final ArrayList<FavoritedMeObject> favoritedMeItems = new ArrayList<FavoritedMeObject>();

    /**
     * Adds a roster item to the packet.
     *
     * @param item a roster item.
     */
    public void addFavoritedMeItem(FavoritedMeObject item) {
        synchronized (favoritedMeItems) {
            favoritedMeItems.add(item);
        }
    }

    public ArrayList<FavoritedMeObject> getFavoritedMeItems() {
        synchronized (favoritedMeItems) {
            return favoritedMeItems;
        }
    }

    public String getChildElementXML() {
        return new StringBuffer()
                .append("<")
                .append(FavoritedMeProvider.elementName())
                .append(" xmlns=\"")
                .append(FavoritedMeProvider.namespace())
                .append("\"/>")
                .toString();
    }
}
