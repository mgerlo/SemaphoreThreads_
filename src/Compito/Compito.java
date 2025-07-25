package Compito;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

class Queue {
    ArrayList<Msg> msgList = new ArrayList<>();
    Semaphore mutex = new Semaphore(1);
    Semaphore pieni = new Semaphore(0);
    Semaphore vuoti;

    public Queue(int L) {
        vuoti = new Semaphore(L);
    }

    public void put(Msg[] msgs) throws InterruptedException {
        int X = msgs.length;
        vuoti.acquire(X);
        mutex.acquire();
        for(Msg msg : msgs) {       // Ciclo for per aggiungere correttamente i messaggi nella coda
            msgList.add(msg);
        }
        mutex.release();
        pieni.release(X);
    }

    public Msg get() throws InterruptedException {
        pieni.acquire();
        mutex.acquire();
        Msg m = msgList.remove(0);      // Rimuove il primo (FIFO)
        mutex.release();
        vuoti.release();
        return m;
    }
}

class Msg {
    int[] array;
    int copyNum;
    ClientThread ct;    // Riferimento al ClientThread mittente (per inviare il risultato)
    int result;         // Variabile dove il WorkerThread inserisce il risultato

    public Msg(int[] array, int copyNum, ClientThread ct) {
        this.array = array;
        this.copyNum = copyNum;
        this.ct = ct;
    }
}

class ClientThread extends Thread {
    int id;
    Queue queue;
    int K, X;
    int TG, DG;
    boolean end = false;
    int baseValue;          // Valore base da cui iniziare la generazione (progressivo)
    int nOp = 0;
    long totalTime = 0;
    Semaphore risposta;     // Sincronizza la ricezione di X risultati

    public ClientThread(int id, Queue queue, int K, int X, int TG, int DG) {
        this.id = id;
        this.queue = queue;
        this.K = K;
        this.X = X;
        this.TG = TG;
        this.DG = DG;
        this.baseValue = id + 1;
    }

    @Override
    public void run() {
        try {
            while(!end) {
                Thread.sleep(TG + (int) (Math.random() * DG));
                int[] array = new int[K];       // Creazione array progressivo di K interi
                for (int i = 0; i < K; i++) {
                    array[i] = baseValue++;
                }
                Msg[] msgs = new Msg[X];        // Creazione X messaggi legati allo stesso array
                for(int i = 0; i < X; i++) {
                    msgs[i] = new Msg(array, i + 1, this);
                }

                risposta = new Semaphore(0);  // Resetta il semaforo per X risposte

                long t0 = System.currentTimeMillis();   // Inizio misurazione tempo
                queue.put(msgs);                        // Inserisce tutti i messaggi nella coda
                risposta.acquire(X);                    // Attende che tutti i risultati arrivino
                long t1 = System.currentTimeMillis();   // Fine misurazione tempo

                StringBuilder sb = new StringBuilder(getName() + " -> Risultati: ");        // Stampa risultati
                for (Msg m : msgs) {
                    sb.append("[").append(m.result).append("] ");
                }
                sb.append("tempo=").append((t1 - t0)).append("ms");
                System.out.println(sb.toString());

                nOp++;      // Aggiornamento statistiche
                totalTime += (t1 - t0);
            }
        } catch (InterruptedException e) { /*Fine thread*/ }
    }

    public void setResult() {       // Notifica dai WorkerThread per completamento di un messaggio
        risposta.release();
    }
}

class WorkerThread extends Thread {
    int TW, DW;
    Queue queue;
    int nOp = 0;

    public WorkerThread(int TW, int DW, Queue queue) {
        this.TW = TW;
        this.DW = DW;
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            while(true) {
                Msg m = queue.get();
                Thread.sleep(TW + (int) (Math.random() * DW));
                int sum = 0;        // Elaborazione WorkerThread: sommaValori * numeroCopia
                for(int v : m.array) {
                    sum += v;
                }
                m.result = sum * m.copyNum;
                m.ct.setResult();       // Notifica al ClientThread la risposta
                nOp++;
            }
        } catch (InterruptedException e) { /*Fine thread*/ }
    }
}

public class Compito {
    public static void main(String[] args) throws InterruptedException {
        int N = 4;
        int M = 3;
        int K = 4;
        int X = 3;
        int TG = 100;
        int DG = 200;
        int TW = 100;
        int DW = 300;
        int L = 20;

        Queue queue = new Queue(L);

        ClientThread[] clients = new ClientThread[N];       // Creazione e avvio ClientThread
        for(int i = 0; i < N; i++) {
            clients[i] = new ClientThread(i, queue, K, X, TG, DG);
            clients[i].setName("CT" + i);
            clients[i].start();
        }

        WorkerThread[] workers = new WorkerThread[M];       // Creazione e avvio WorkerThread
        for(int i = 0; i < M; i++) {
            workers[i] = new WorkerThread(TW, DW, queue);
            workers[i].setName("WT" + i);
            workers[i].start();
        }

        Thread.sleep(10000);

        for(ClientThread ct : clients) {        // Fase conclusiva: terminare i ClientThread
            ct.end = true;
        }
        for(ClientThread ct : clients) {
            ct.join();
        }

        for(WorkerThread wt : workers) {        // Ciclo per interrompere gli WorkerThread
            wt.interrupt();
            wt.join();
        }

        System.out.println("____RISULTATI____");        // Stampa statistiche finali
        int totalOp = 0;
        long totalTime = 0;
        for (ClientThread ct : clients) {
            System.out.println(ct.getName() + ": op=" + ct.nOp + ", tempoMedio=" +
                    (ct.nOp > 0 ? (ct.totalTime / ct.nOp) : 0) + "ms");
            totalOp += ct.nOp;
            totalTime += ct.totalTime;
        }

        for (WorkerThread wt : workers) {
            System.out.println(wt.getName() + ": op=" + wt.nOp);
        }

        System.out.println("Totale operazioni Client: " + totalOp);
        System.out.println("Tempo medio complessivo: " +
                (totalOp > 0 ? (totalTime / totalOp) : 0) + "ms");
    }
}
