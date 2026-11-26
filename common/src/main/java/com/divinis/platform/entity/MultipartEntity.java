package com.divinis.platform.entity;

/** A creature made of PartEntity parts (registered with PartRegistry while it is in a level). */
public interface MultipartEntity {
    PartEntity<?>[] getDivinisParts();
}
