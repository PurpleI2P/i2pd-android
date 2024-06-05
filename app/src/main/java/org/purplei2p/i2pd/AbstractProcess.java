package org.purplei2p.i2pd;

public interface AbstractProcess {
    /** @param tr can be null
     */
    void kill(Throwable tr);
}