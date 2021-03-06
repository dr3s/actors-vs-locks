package com.cerner.devcon.actor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.Procedure;

/**
 * BankTransfer actor encapsulates behavior and state to perform a single
 * transaction.
 * 
 * It is designed as a finite state machine (FSM) which starts in a state that
 * is waiting for a transfer message and transitions through other states as it
 * completes the transaction.
 */
public class BankTransfer extends UntypedActor {

	LoggingAdapter log = Logging.getLogger(getContext().system(), this);

	/**
	 * Handles just the Transfer message.
	 * 
	 * Uses become() to transition behavior as the state of the transaction
	 * changes. Note the lack of state in the actor, as it passes the state
	 * along with the new behavior that the actor becomes.
	 */
	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof Transfer) {
			log.debug("received transfer message");
			Transfer txfr = (Transfer) msg;
			// Send an async msg to the from account to withdraw
			txfr.from.tell(new BankAccount.Withdraw(txfr.getAmount()),
					getSelf());
			// Change the behavior of the actor to wait for the result of the
			// withdrawal
			getContext().become(
					new AwaitFrom(txfr.to, txfr.amount, getSender()));
		}

	}

	/**
	 * Class that defines behavior of the actor while it is awaiting a response
	 * from the From account.
	 */
	private class AwaitFrom implements Procedure<Object> {

		private ActorRef to;
		private double amount;
		private ActorRef customer;

		public AwaitFrom(final ActorRef to, final double amount,
				final ActorRef customer) {
			this.to = to;
			this.amount = amount;
			this.customer = customer;
		}

		@Override
		public void apply(Object msg) {
			if (msg instanceof BankAccount.TransactionStatus) {
				BankAccount.TransactionStatus status = (BankAccount.TransactionStatus) msg;
				switch (status) {
				case DONE:
					log.debug("received transfer withdraw done");
					to.tell(new BankAccount.Deposit(amount), getSelf());
					getContext().become(new AwaitTo(customer));
					break;
				case FAILED:
					log.debug("received transfer withdraw failed");
					customer.tell(TransferStatus.FAILED, getSelf());
					getContext().stop(getSelf());
					break;
				}
			}
		}

	};

	/**
	 * Class that defines behavior of the actor while it is awaiting a response
	 * from the To account.
	 */
	private class AwaitTo implements Procedure<Object> {

		private ActorRef customer;

		public AwaitTo(final ActorRef customer) {
			this.customer = customer;
		}

		@Override
		public void apply(Object msg) {
			if (msg instanceof BankAccount.TransactionStatus) {
				BankAccount.TransactionStatus status = (BankAccount.TransactionStatus) msg;
				switch (status) {
				case DONE:
					log.debug("received transfer deposit done");
					customer.tell(TransferStatus.DONE, getSelf());
					getContext().stop(getSelf());
					break;
				case FAILED:
					log.debug("received transfer deposit failed");
					customer.tell(TransferStatus.FAILED, getSelf());
					getContext().stop(getSelf());
					break;
				}
			}
		}

	};

	public static class Transfer {
		private double amount;
		private ActorRef from;
		private ActorRef to;

		public Transfer(ActorRef from, ActorRef to, double amount) {
			this.amount = amount;
			this.from = from;
			this.to = to;
		}

		public double getAmount() {
			return amount;
		}

		public ActorRef getFrom() {
			return from;
		}

		public ActorRef getTo() {
			return to;
		}

	}

	public static enum TransferStatus {
		DONE, FAILED;
	}
}
