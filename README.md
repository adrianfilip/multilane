####### MultiLaneSequencer #######

 Given the following situation
 L - lane
 P - program
        L1   L2   L3  L4
  P1    X          X
  P2    X
  P3         X        X
  P4    X    X

 The order in which the programs above are executed is guaranteed to be:
 – P1 and P3 can be executed concurrently because their lanes are free (I use can instead of will  because depends on your available threads)
 – P2 is queued up behind P1
 – P4 is queued up behind P2 and P3, so until they are both executed it just waits in a non blocking fashion




####### MultiLaneLocker #######

 Given the following situation
 L - lane
 P - program
        L1   L2   L3  L4
  P1    X          X
  P2    X
  P3         X        X
  P4    X    X

 MultiLaneLocker guarantees that:
 - P1 - P2 - P4 will run one at a time
 - P3 - P4 will run one at a time
 but it makes no quarantees about the order in which they run




