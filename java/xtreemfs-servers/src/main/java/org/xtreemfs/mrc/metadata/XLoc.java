/*
 * Copyright (c) 2008-2011 by Jan Stender,
 *               Zuse Institute Berlin
 *
 * Licensed under the BSD License, see LICENSE file for details.
 *
 */

package org.xtreemfs.mrc.metadata;


/**
 * 用于访问一个文件的单个副本（xloc）的接口，x-locations 存储的是xloc 列表 和文件存储位置信息
 *
 *
 * Interface for accessing information about single replica (X-Location) of a
 * file. X-Locations are stored in a file's X-Locations List and contain
 * information about file data storage locations.
 */
public interface XLoc {


    /**
     * osd副本数
     * The number of OSDs in the replica.
     *  
     * @return the number of OSDs
     */
    public short getOSDCount();
    
    /**
     * 返回指定索引位置的osd的uuid
     *
     * Returns the OSD UUID at the given index position.
     * 
     * @param index the index of the OSD UUID
     * @return the OSD UUID at index position <code>position</code>
     */
    public String getOSD(int index);
    
    /**
     * 返回分配给副本的分片策略
     * Returns the striping policy assigned to the replica.
     * 
     * @return the striping policy
     */
    public StripingPolicy getStripingPolicy();
    
    /**
     * 返回指定副本的复制标志
     * Returns the replication flags assigned to the replica
     * 
     * @return the replication flags
     */
    public int getReplicationFlags();
    
    /**
     * 为指定副本分配复制标志
     * Assigns new replication flags to the replica.
     * 
     * @param replFlags the replication flags
     */
    public void setReplicationFlags(int replFlags);
}