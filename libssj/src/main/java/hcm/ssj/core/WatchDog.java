/*
 * WatchDog.java
 * Copyright (c) 2016
 * Authors: Ionut Damian, Michael Dietz, Frank Gaibler, Daniel Langerenken
 * *****************************************************
 * This file is part of the Social Signal Interpretation for Java (SSJ) framework
 * developed at the Lab for Human Centered Multimedia of the University of Augsburg.
 *
 * SSJ has been inspired by the SSI (http://openssi.net) framework. SSJ is not a
 * one-to-one port of SSI to Java, it is an approximation. Nor does SSJ pretend
 * to offer SSI's comprehensive functionality and performance (this is java after all).
 * Nevertheless, SSJ borrows a lot of programming patterns from SSI.
 *
 * This library is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this library; if not, see <http://www.gnu.org/licenses/>.
 */

package hcm.ssj.core;

/**
 * Created by Johnny on 05.03.2015.
 */
public class WatchDog extends Thread {

    protected String _name = "SSJ_WatchDog";

    protected boolean _terminate = false;
    protected boolean _safeToKill = false;

    protected Timer _timer;
    protected boolean _targetCheckedIn = false;

    protected final Object _lock = new Object();

    protected TheFramework _frame;

    protected int _bufferID;
    protected double _watchInterval;
    protected double _syncInterval;
    protected int _syncIter;
    protected int _watchIter;

    public WatchDog(int bufferID, double watchInterval, double syncInterval)
    {
        _frame = TheFramework.getFramework();

        _bufferID = bufferID;
        _watchInterval = watchInterval;
        _syncInterval = syncInterval;

        double sleep = 0;
        if (watchInterval > 0 && syncInterval > 0) {
            sleep = Math.min (watchInterval, syncInterval);
        } else if (syncInterval > 0) {
            sleep = syncInterval;
        } else if (watchInterval > 0) {
            sleep = watchInterval;
        }

        _syncIter = (int)(syncInterval / sleep) -1;
        _watchIter = (int)(watchInterval / sleep) -1;

        _timer = new Timer(sleep);

        start();
    }

    public void checkIn()
    {
        synchronized (_lock)
        {
            _targetCheckedIn = true;
        }
    }

    @Override
    public void run()
    {
        //wait for framework
        while (!_frame.isRunning()) {
            try {
                sleep(Cons.SLEEP_ON_IDLE);
            } catch (InterruptedException e) {}
        }

        int syncIterCnt = _syncIter;
        int watchIterCnt = _watchIter;

        _timer.reset();

        while(!_terminate && _frame.isRunning())
        {
            try {
                //check buffer watch
                if(watchIterCnt == 0)
                {
                    synchronized (_lock)
                    {
                        if (!_targetCheckedIn)
                        {
                            //provider did not check in, provide zeroes
                            _frame.pushZeroes(_bufferID);
                        }
                        _targetCheckedIn = false;
                    }
                    watchIterCnt = _watchIter;
                }
                else watchIterCnt--;

                //check buffer sync
                if(syncIterCnt == 0)
                {
                    _frame.sync(_bufferID);
                    syncIterCnt = _syncIter;
                }
                else syncIterCnt--;

                _timer.sync();

            } catch(Exception e) {
                _frame.crash(this.getClass().getSimpleName(), "exception in loop", e);
            }
        }

        _safeToKill = true;
    }

    public void close() throws InterruptedException
    {
        Log.i("shutting down");

        _terminate = true;

        while(!_safeToKill)
            sleep(Cons.SLEEP_ON_TERMINATE);

        Log.i("shut down complete");
    }
}