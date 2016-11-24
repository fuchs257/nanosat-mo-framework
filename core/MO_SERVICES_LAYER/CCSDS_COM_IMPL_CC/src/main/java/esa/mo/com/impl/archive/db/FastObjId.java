/* ----------------------------------------------------------------------------
 * Copyright (C) 2015      European Space Agency
 *                         European Space Operations Centre
 *                         Darmstadt
 *                         Germany
 * ----------------------------------------------------------------------------
 * System                : ESA NanoSat MO Framework
 * ----------------------------------------------------------------------------
 * Licensed under the European Space Agency Public License, Version 2.0
 * You may not use this file except in compliance with the License.
 *
 * Except as expressly set forth in this License, the Software is provided to
 * You on an "as is" basis and without warranties of any kind, including without
 * limitation merchantability, fitness for a particular purpose, absence of
 * defects or errors, accuracy or non-infringement of intellectual property rights.
 * 
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 * ----------------------------------------------------------------------------
 */
package esa.mo.com.impl.archive.db;

import esa.mo.com.impl.util.HelperCOM;
import esa.mo.helpertools.helpers.HelperMisc;
import java.util.HashMap;
import javax.persistence.Query;
import org.ccsds.moims.mo.com.structures.ObjectType;
import org.ccsds.moims.mo.mal.structures.IdentifierList;

/**
 *
 *
 */
public class FastObjId {

    private final DatabaseBackend dbBackend;
    private HashMap<Key, Long> fastID;
//    private final Semaphore semaphore = new Semaphore(1);

    public FastObjId(final DatabaseBackend dbBackend) {
        this.dbBackend = dbBackend;
        this.fastID = new HashMap<Key, Long>();
    }

    public synchronized void resetFastIDs() {
        this.fastID = new HashMap<Key, Long>();
    }

    private Long newUniqueID(final ObjectType objectTypeId, final IdentifierList domain) {

        Long objId = (this.getCurrentID(objectTypeId, domain));
        if (objId == null) {
            return null;
        }

        objId++;
        this.setUniqueID(objectTypeId, domain, objId);

        return objId;
    }

    private void setUniqueIdIfLatest(final ObjectType objectTypeId, final IdentifierList domain, final Long objId) {
        final Key key = new Key(objectTypeId, domain);
        final Long currentObjId = this.getCurrentID(objectTypeId, domain);

        if (currentObjId == null) {
            this.fastID.put(key, objId);
            return;
        }

        if (objId > currentObjId) {
            this.fastID.put(key, objId);
        }
    }

    private void setUniqueID(final ObjectType objectTypeId, final IdentifierList domain, final Long objId) {
        Key key = new Key(objectTypeId, domain);
        this.fastID.put(key, objId);
    }

    public synchronized void delete(final ObjectType objectTypeId, final IdentifierList domain) {
        Key key = new Key(objectTypeId, domain);
        this.fastID.remove(key);
    }

    private Long getCurrentID(final ObjectType objectTypeId, final IdentifierList domain) {
        final Key key = new Key(objectTypeId, domain);
        final Long objId = this.fastID.get(key);

        if (objId == null) {
            return null;
        }

        return objId;
    }

    private synchronized Long generateUniqueObjId(final ObjectType objectType, final IdentifierList domain) {

        // Did we request this objType+domain combination before?! If so, return the next value
        Long objId = this.newUniqueID(objectType, domain);
        if (objId != null) {
            return objId;
        }

        // Well, if not then we must check if this combination already exists in the PU...
        dbBackend.createEntityManager();
        Query query = dbBackend.getEM().createQuery("SELECT MAX(PU.objId) FROM ArchivePersistenceObject PU WHERE PU.objectTypeId=:objectTypeId AND PU.domainId=:domainId");
        query.setParameter("objectTypeId", HelperCOM.generateSubKey(objectType));
        query.setParameter("domainId", HelperMisc.domain2domainId(domain));
        Long maxValue = (Long) query.getSingleResult();
        dbBackend.closeEntityManager();

        if (maxValue == null) {
            this.setUniqueID(objectType, domain, (long) 0); // The object does not exist in PU
        } else {
            this.setUniqueID(objectType, domain, maxValue);
        }

        objId = this.newUniqueID(objectType, domain);

        return objId;
    }

    public synchronized Long getUniqueObjId(final ObjectType objType, final IdentifierList domain, final Long objId) {
        Long outObjId;

        if (objId == 0) { // requirement: 3.4.6.2.5
            outObjId = this.generateUniqueObjId(objType, domain);
        } else {
            this.setUniqueIdIfLatest(objType, domain, objId); // Check if it is not greater than the current "fast" objId
            outObjId = objId;
        }

        return outObjId;
    }

    /*
    private boolean exists(final ObjectType objectTypeId, final IdentifierList domain) {
        Key key = new Key(objectTypeId, domain);
        return (this.fastID.get(key) != null);
    }
     */
    private class Key {

        private final Long objectTypeId;
        private final String domainId;

        protected Key(final ObjectType objectTypeId, final IdentifierList domain) {
            this.objectTypeId = HelperCOM.generateSubKey(objectTypeId);
            this.domainId = HelperMisc.domain2domainId(domain);
        }

        private Long getObjTypeId() {
            return this.objectTypeId;
        }

        private String getDomainId() {
            return this.domainId;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) {
                return false;
            }
            Key input = (Key) other;
            if (!input.getObjTypeId().equals(objectTypeId)) {
                return false;
            }
            return input.getDomainId().equals(domainId);
        }

        @Override
        public int hashCode() {
            return objectTypeId.hashCode() ^ domainId.hashCode();
        }

    }

}