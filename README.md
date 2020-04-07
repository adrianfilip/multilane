## MultiLaneSequencer

 Given the following scenario
 
 * L - lane
 * P - program

 | timestep | program | L1 |  L2 |  L3 |  L4 |
 |----------|---------|----|-----|-----|-----|
 | t0       | P1      | X  |  X  |     |     |
 | t1       | P2      | X  |     |     |     |
 | t2       | P3      |    |     | X   | X   |
 | t3       | P4      |    | X   | X   |     |

Where `t0 < t1 < t2 < t3`
 The order in which the programs above are executed is guaranteed to be:
 1. P1 and P3 can be executed concurrently because their lanes are free (I use can instead of will  because depends on your available threads)
 2. P2 is queued up behind P1
 3. P4 is queued up behind P2 and P3, so until they are both executed it just waits in a non blocking fashion




## MultiLaneLocker

 Given the following scenario
 * L - lane
 * P - program

 |timestep| program |      L1 |  L2 |  L3 | L4 |
 |--------|---------|---------|-----|-----|----|
 |t0| P1  |  X | |  |       X |
 |t1| P2  |  X | | | |
 |t2| P3  |    |   X  |  |    X|
 |t3| P4  |  X  |  X | | |

where `t0 < t1 < t2 < t3`
 MultiLaneLocker guarantees that:
 1. P1 - P2 - P4 will run one at a time
 2. P3 - P4 will run one at a time

but it makes no guarantees about the order in which they run




