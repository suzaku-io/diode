package diode.dev

import org.scalajs.dom
import org.scalajs.dom.{Event, IDBDatabase, IDBObjectStore, IDBTransactionMode}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.scalajs.js

class PersistStateIDB[M <: AnyRef, P <: js.Any](pickleF: M => P, unpickleF: P => M) extends PersistState[M, P] {
  final val storeName = "snapshots"

  // Opens (and creates) a database for snapshots
  // Contains the DB in a future, for easy async access
  private lazy val dbF = {
    dom.window.indexedDB
      .map(Future.successful)
      .getOrElse(Future.failed(new Exception("IndexedDB is not supported")))
      .flatMap { indexedDb =>
        val req = indexedDb.open("DiodePersistence")
        val p   = Promise[IDBDatabase]()
        req.onsuccess = (_: Event) => {
          p.success(req.result.asInstanceOf[IDBDatabase])
        }
        req.onupgradeneeded = (_: Event) => {
          val db = req.result.asInstanceOf[IDBDatabase]
          db.createObjectStore(storeName)
        }
        req.onerror = (_: Event) => {
          p.failure(new Exception(s"IDB error ${req.error.name}"))
        }
        p.future
      }
  }

  private def withStore[A](f: IDBObjectStore => Future[A]) = {
    dbF.flatMap { db =>
      val tx    = db.transaction(storeName, IDBTransactionMode.readwrite)
      val store = tx.objectStore(storeName)
      f(store)
    }
  }

  override def pickle(model: M): P = pickleF(model)

  override def unpickle(pickled: P): M = unpickleF(pickled)

  override def save(id: String, pickled: P): Unit = {
    withStore { store =>
      val p   = Promise[String]()
      val req = store.put(pickled, id)
      req.onsuccess = (_: Event) => {
        p.success(req.result.asInstanceOf[String])
      }
      req.onerror = (_: Event) => {
        p.failure(new Exception(s"IDB error ${req.error.name}"))
      }
      p.future
    }
  }

  override def load(id: String): Future[P] = {
    withStore { store =>
      val p   = Promise[P]()
      val req = store.get(id)
      req.onsuccess = (_: Event) => {
        p.success(req.result.asInstanceOf[P])
      }
      req.onerror = (_: Event) => {
        p.failure(new Exception(s"IDB error ${req.error.name}"))
      }
      p.future
    }
  }
}
