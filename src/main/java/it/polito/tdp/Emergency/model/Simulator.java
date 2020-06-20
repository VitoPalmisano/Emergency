package it.polito.tdp.Emergency.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import it.polito.tdp.Emergency.model.Event.EventType;
import it.polito.tdp.Emergency.model.Paziente.CodiceColore;

public class Simulator {

	// PARAMETRI DI SIMULAZIONE
	private int NS = 5; // numero di studi medici
	
	private int NP = 1000; // numero pazienti
	private Duration T_ARRIVAL = Duration.ofMinutes(5); // intervallo tra i pazienti
	
	private final Duration DURATION_TRIAGE = Duration.ofMinutes(5);
	private final Duration DURATION_WHITE = Duration.ofMinutes(10);
	private final Duration DURATION_YELLOW = Duration.ofMinutes(15);
	private final Duration DURATION_RED = Duration.ofMinutes(30);
	
	private final Duration WHITE_TIMEOUT = Duration.ofMinutes(90);
	private final Duration YELLOW_TIMEOUT = Duration.ofMinutes(30);
	private final Duration RED_TIMEOUT = Duration.ofMinutes(60);
	
	private final LocalTime oraInizio = LocalTime.of(8, 0);
	private final LocalTime oraFine = LocalTime.of(20, 0);
	
	private final Duration TICK_TIME = Duration.ofMinutes(5);
	
	// OUTPUT DA CALCOLARE
	private int pazientiTot;
	private int pazientiDimessi;
	private int pazientiAbbandonano;
	private int pazientiMorti;
	
	// STATO DI SISTEMA
	private List<Paziente> pazienti;
	private PriorityQueue<Paziente> attesa; // post-triage prima di essere chiamati
	private int studiLiberi;
	
	private CodiceColore coloreAssegnato;
	
	// CODA DEGLI EVENTI
	private PriorityQueue<Event> queue;
	
	// INIZIALIZZAZIONE
	public void init() {
		this.queue = new PriorityQueue<Event>();
		
		this.pazienti = new ArrayList<Paziente>();
		this.attesa = new PriorityQueue<Paziente>();
		
		this.pazientiTot = 0;
		this.pazientiDimessi = 0;
		this.pazientiAbbandonano = 0;
		this.pazientiMorti = 0;
		
		this.studiLiberi = this.NS;
		
		this.coloreAssegnato = CodiceColore.WHITE;
		
		// Generiamo gli eventi iniziali
		int nPaz = 0;
		LocalTime oraArrivo = this.oraInizio;
		
		while(nPaz < this.NP && oraArrivo.isBefore(oraFine)) {
			Paziente p = new Paziente(oraArrivo, CodiceColore.UNKNOWN);
			pazienti.add(p);
			Event e = new Event(oraArrivo, EventType.ARRIVAL, p);
			queue.add(e);
			
			nPaz++;
			oraArrivo = oraArrivo.plus(T_ARRIVAL);
		}
		
		// Genere TICK iniziale
		queue.add(new Event(this.oraInizio, EventType.TICK, null));
	}
	
	// ESECUZIONE
	public void run() {
		
		while(!queue.isEmpty()) {
			Event e = this.queue.poll();
			processEvent(e);
		}
		
	}
	
	private void processEvent(Event e) {
		
		Paziente paz = e.getPaziente();
		
		switch(e.getType()) {
		case ARRIVAL:
			// arriva un paziente: tra 5 minuti sara' finito il triage
			queue.add(new Event(e.getTime().plus(DURATION_TRIAGE), EventType.TRIAGE, paz));
			this.pazientiTot++;
			break;
			
		case TRIAGE:
			// assegnare codice colore
			paz.setColore(nuovoCodiceColore());
			
			// mette in lista d'attesa
			attesa.add(paz);
			
			// schedula timeout
			if(paz.getColore() == CodiceColore.WHITE)
				queue.add(new Event(e.getTime().plus(WHITE_TIMEOUT), EventType.TIMEOUT, paz));
			else if(paz.getColore() == CodiceColore.YELLOW)
				queue.add(new Event(e.getTime().plus(YELLOW_TIMEOUT), EventType.TIMEOUT, paz));
			else if(paz.getColore() == CodiceColore.RED)
				queue.add(new Event(e.getTime().plus(RED_TIMEOUT), EventType.TIMEOUT, paz));
			
			break;
			
		case FREE_STUDIO:
			
			Paziente prossimo = attesa.poll();
			if(prossimo != null) {
				// fallo entrare
				this.studiLiberi--;
				
				// schedula l'uscita dallo studio
				if(prossimo.getColore() == CodiceColore.WHITE)
					queue.add(new Event(e.getTime().plus(DURATION_WHITE), EventType.TREATED, prossimo));
				else if(prossimo.getColore() == CodiceColore.YELLOW)
					queue.add(new Event(e.getTime().plus(DURATION_YELLOW), EventType.TREATED, prossimo));
				else if(prossimo.getColore() == CodiceColore.RED)
					queue.add(new Event(e.getTime().plus(DURATION_RED), EventType.TREATED, prossimo));
			}
			
			break;
			
		case TREATED:
			// libera lo studio
			this.studiLiberi++;
			
			// il paziente esce
			paz.setColore(CodiceColore.OUT);
			
			this.pazientiDimessi++;
			this.queue.add(new Event(e.getTime(), EventType.FREE_STUDIO, null));
			
			break;
			
		case TIMEOUT:
			
			// esci dalla lista d'attesa
			boolean eraPresente = attesa.remove(paz);
			if(!eraPresente)
				break;
			
			switch(paz.getColore()) {
			case WHITE:
				// va a casa
				this.pazientiAbbandonano++;
				break;
			
			case YELLOW:
				// diventa RED
				paz.setColore(CodiceColore.RED);
				attesa.add(paz);
				queue.add(new Event(e.getTime().plus(RED_TIMEOUT), EventType.TIMEOUT, paz));
				break;
				
			case RED:
				// MUORE
				this.pazientiMorti++;
				paz.setColore(CodiceColore.BLACK);
				break;
			}
			
			break;
			
		case TICK:
			if(this.studiLiberi>0) {
				queue.add(new Event(e.getTime(), EventType.FREE_STUDIO, null));
			}
			
			if(e.getTime().isBefore(LocalTime.of(23, 30))) // Altrimenti la simulazione va avanti all'infinito solo con i tick
				this.queue.add(new Event(e.getTime().plus(TICK_TIME), EventType.TICK, null));
			break;
		}
				
	}
	
	private CodiceColore nuovoCodiceColore() {
		CodiceColore nuovo = coloreAssegnato;
		
		if(coloreAssegnato == CodiceColore.WHITE)
			coloreAssegnato=CodiceColore.YELLOW;
		else if(coloreAssegnato == CodiceColore.YELLOW)
			coloreAssegnato=CodiceColore.RED;
		else
			coloreAssegnato=CodiceColore.WHITE;
		
		return nuovo;
	}

	public int getNS() {
		return NS;
	}
	public void setNS(int nS) {
		NS = nS;
	}

	public int getNP() {
		return NP;
	}

	public void setNP(int nP) {
		NP = nP;
	}

	public Duration getT_ARRIVAL() {
		return T_ARRIVAL;
	}

	public void setT_ARRIVAL(Duration t_ARRIVAL) {
		T_ARRIVAL = t_ARRIVAL;
	}

	public int getPazientiTot() {
		return pazientiTot;
	}

	public int getPazientiDimessi() {
		return pazientiDimessi;
	}

	public int getPazientiAbbandonano() {
		return pazientiAbbandonano;
	}

	public int getPazientiMorti() {
		return pazientiMorti;
	}
	
	
}
