package edu.grossmont.p2p;

/**
 * Created by rgillespie on 8/6/2018.
 */
public class P2PMessageQueue {

    private P2PMessage head = null;
    private P2PMessage tail = null;


    public synchronized void enqueue(P2PMessage oMessage){

		if (head == null) {
			// The queue is empty, set both head and tail to the new message.
			head = oMessage;
			tail = oMessage;
		} else {
			// Append the message to the end of the queue and update the tail.

			tail.next = oMessage;
			tail = oMessage;
		}
    }


    public synchronized P2PMessage dequeue(){
		if (head == null) {
			return null;
		} else {

			P2PMessage temp = head;
			head = head.next;
			return temp;
		}
    }


    public boolean hasNodes(){
		if (head == null) {
			return false;
		} else {
			return true;
		}
    }
}
