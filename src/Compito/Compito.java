package Compito;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class Compito {

    public static void main(String[] args) throws InterruptedException {
        int N = 4; // ClientThread
        int M = 3; // WorkerThread
        int K = 4; // dimensione array
        int X = 3; // copie per ogni array
        int L = 20; // lunghezza coda limitata
        int TG = 100; // tempo base generazione
        int DG = 200; // delta generazione
        int TW = 100; // tempo base worker
        int DW = 300; // delta worker

        MsgQueue queue = new MsgQueue(L);

        ClientThread[] clients = new ClientThread[N];
        for (int i = 0; i < N; i++) {
            clients[i] = new ClientThread(i, queue, K, X, TG, DG);
            clients[i].setName("CT" + i);
            clients[i].start();
        }

        WorkerThread[] workers = new WorkerThread[M];
        for (int i = 0; i < M; i++) {
            workers[i] = new WorkerThread(queue, TW, DW);
            workers[i].setName("WT" + i);
            workers[i].start();
        }

        Thread.sleep(10000); // durata esecuzione

        for (ClientThread ct : clients) {
            ct.end = true;
        }
        for (ClientThread ct : clients) {
            ct.join();
        }

        for (WorkerThread wt : workers) {
            wt.interrupt();
            wt.join();
        }

        System.out.println("--- RISULTATI ---");
        int totalOp = 0;
        long totalTime = 0;
        for (ClientThread ct : clients) {
            System.out.println(ct.getName() + ": op=" + ct.nOp + ", avgTime=" +
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

class Msg {
    int copyNum;
    int[] array;
    ClientThread ct;
    int result;

    public Msg(ClientThread ct, int[] array, int copyNum) {
        this.ct = ct;
        this.array = array;
        this.copyNum = copyNum;
    }
}

class MsgQueue {
    ArrayList<Msg> data = new ArrayList<>();
    Semaphore mutex = new Semaphore(1);
    Semaphore piene = new Semaphore(0);
    Semaphore vuote;

    public MsgQueue(int L) {
        vuote = new Semaphore(L);
    }

    public void add(Msg[] msgs) throws InterruptedException {
        vuote.acquire(msgs.length);
        mutex.acquire();
        for (Msg m : msgs) {
            data.add(m);
        }
        mutex.release();
        piene.release(msgs.length);
    }

    public Msg get() throws InterruptedException {
        piene.acquire();
        mutex.acquire();
        Msg m = data.remove(0);
        mutex.release();
        vuote.release();
        return m;
    }
}

class ClientThread extends Thread {
    int id;
    MsgQueue queue;
    int K, X;
    int TG, DG;
    boolean end = false;
    int baseValue;
    int nOp = 0;
    long totalTime = 0;
    Semaphore risposta;

    public ClientThread(int id, MsgQueue queue, int K, int X, int TG, int DG) {
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
            while (!end) {
                Thread.sleep(TG + (int) (Math.random() * DG));
                int[] array = new int[K];
                for (int i = 0; i < K; i++) {
                    array[i] = baseValue++;
                }

                Msg[] msgs = new Msg[X];
                for (int i = 0; i < X; i++) {
                    msgs[i] = new Msg(this, array, i + 1);
                }

                risposta = new Semaphore(0);
                long t0 = System.currentTimeMillis();
                queue.add(msgs);
                risposta.acquire(X);
                long t1 = System.currentTimeMillis();

                StringBuilder sb = new StringBuilder(getName() + " -> Risultati: ");
                for (Msg m : msgs) {
                    sb.append("[").append(m.result).append("] ");
                }
                sb.append("tempo=").append((t1 - t0)).append("ms");
                System.out.println(sb.toString());

                nOp++;
                totalTime += (t1 - t0);
            }
        } catch (InterruptedException e) {
        }
    }

    public void setResult() {
        risposta.release();
    }
}

class WorkerThread extends Thread {
    MsgQueue queue;
    int TW, DW;
    int nOp = 0;

    public WorkerThread(MsgQueue queue, int TW, int DW) {
        this.queue = queue;
        this.TW = TW;
        this.DW = DW;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Msg m = queue.get();
                Thread.sleep(TW + (int) (Math.random() * DW));
                int sum = 0;
                for (int v : m.array) {
                    sum += v;
                }
                m.result = sum * m.copyNum;
                m.ct.setResult();
                nOp++;
            }
        } catch (InterruptedException e) {
        }
    }
}
