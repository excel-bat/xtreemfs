package org.xtreemfs.osd.replication;

/*  Copyright (c) 2008 Konrad-Zuse-Zentrum fuer Informationstechnik Berlin.

 This file is part of XtreemFS. XtreemFS is part of XtreemOS, a Linux-based
 Grid Operating System, see <http://www.xtreemos.eu> for more details.
 The XtreemOS project has been developed with the financial support of the
 European Commission's IST program under contract #FP6-033576.

 XtreemFS is free software: you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free
 Software Foundation, either version 2 of the License, or (at your option)
 any later version.

 XtreemFS is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with XtreemFS. If not, see <http://www.gnu.org/licenses/>.
 */
/*
 * AUTHORS: Christian Lorenz (ZIB)
 */

import java.util.ArrayList;
import java.util.List;

import org.xtreemfs.common.ServiceAvailability;
import org.xtreemfs.common.uuids.ServiceUUID;
import org.xtreemfs.common.xloc.XLocations;

/**
 * This class provides the basic functionality needed by the different transfer strategies. One
 * TransferStrategy manages a whole file (all objects of this file).
 * <br>warning: this class is NOT thread-safe
 * <br>09.09.2008
 */
public abstract class TransferStrategy {
    /**
     * Encapsulates the "returned"/chosen values.
     * <br>12.02.2009
     */
    public class NextRequest {
        public ServiceUUID osd = null;
        public long objectNo = -1;
        /**
         * if true, the OSD must return a list of all local available objects
         */
        public boolean requestObjectList = false;
        
        boolean isAllSet() {
            return (osd != null) && (objectNo != -1);
        }
    }
    
    public static class TransferStrategyException extends Exception {
        public enum ErrorCode {
            NO_OSD_REACHABLE,
            NO_OSD_FOUND,
//            OBJECT_MUST_BE_HOLE            
        }
 
        private final ErrorCode errorCode;
        /**
         * 
         */
        public TransferStrategyException(String message, ErrorCode errorCode) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    protected String fileID;
    protected XLocations xLoc;

    /**
     * contains the chosen values for the next replication request
     */
    private NextRequest next;

    /**
     * contains all not preferred objects which must be replicated (e.g. background-replication)
     */
    protected List<Long> requiredObjects;

    /**
     * contains all objects which must be replicated first (e.g. client-request)
     */
    protected List<Long> preferredObjects; // requested objects

    /**
     * checks if the OSD is available (e.g. network interrupt)
     */
    protected final ServiceAvailability osdAvailability;

    /**
     * @param rqDetails
     */
    protected TransferStrategy(String fileID, XLocations xLoc, ServiceAvailability osdAvailability) {
        super();
        this.xLoc = xLoc;
        this.fileID = fileID;
        this.requiredObjects = new ArrayList<Long>();
        this.preferredObjects = new ArrayList<Long>();
        this.osdAvailability = osdAvailability;
//        this.availableObjectsOnOSD = new HashMap<ServiceUUID, List<Long>>();
        this.next = null;
    }
    
    public void updateXLoc(XLocations xLoc) {
        this.xLoc = xLoc;
    }

    /**
     * chooses the next object, which will be replicated
     */
    public void selectNext() throws TransferStrategyException {
        this.next = null;

        if (this.getObjectsCount() > 0) {
            assert (this.preferredObjects.size() > 0 || this.requiredObjects.size() > 0);

            NextRequest next = selectNextHook();
            if (next != null && next.isAllSet())
                this.next = next;
        }
    }

    /**
     * maskerades if an object could not be used at the moment as it is using another object
     * @return
     * @throws TransferStrategyException
     */
    protected abstract NextRequest selectNextHook() throws TransferStrategyException;

    /**
     * 
     */
    public void selectNextOSD(long objectNo) throws TransferStrategyException {
        this.next = null;
        NextRequest next = selectNextOSDHook(objectNo);
        if (next != null && next.isAllSet())
            this.next = next;
    }

    protected abstract NextRequest selectNextOSDHook(long objectNo) throws TransferStrategyException;

    /**
     * Returns the "result" from selectNext().
     * 
     * @return null, if selectNext() has not been executed before (since getNext() was called last time) or no object to fetch exists
     * @see java.util.ArrayList#add(java.lang.Object)
     */
    public NextRequest getNext() {
        if (next != null) {
            // remove object from lists, so it can't be chosen twice
            removeObjectFromList(next.objectNo);
        }
        return next;
    }

    /**
     * add an object which must be replicated
     * 
     * @param objectNo
     * @param preferred
     * @return
     * @see java.util.ArrayList#add(java.lang.Object)
     */
    public boolean addObject(long objectNo, boolean preferred) {
        if (preferred) {
            // object must not contained in both lists
            if (requiredObjects.contains(objectNo))
                requiredObjects.remove(objectNo);
            // no multiple entries
            if (!preferredObjects.contains(objectNo))
                return preferredObjects.add(objectNo);
        } else {
            // object must not contained in both lists
            if (preferredObjects.contains(objectNo))
                preferredObjects.remove(objectNo);
            // no multiple entries
            if (!requiredObjects.contains(objectNo))
                return requiredObjects.add(objectNo);
        }
        return false;
    }

    /**
     * removes the objectNo only from the list of replicating objects (called internally)
     * 
     * @param objectNo
     * @return
     * @see java.util.ArrayList#remove(java.lang.Object)
     */
    protected boolean removeObjectFromList(long objectNo) {
        boolean contained = preferredObjects.remove(objectNo);
        contained = contained || requiredObjects.remove(objectNo);
        return contained;
    }

    /**
     * remove an object which need not be replicated anymore
     * 
     * @param objectNo
     * @return
     * @see java.util.ArrayList#remove(java.lang.Object)
     */
    public abstract boolean removeObject(long objectNo);

    /**
     * Returns how much objects will be replicated.
     * 
     * @return
     */
    public int getObjectsCount() {
        return preferredObjects.size() + requiredObjects.size();
    }
}
