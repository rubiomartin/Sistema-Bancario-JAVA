import { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import './App.css';

const API_BASE = import.meta.env.VITE_API_URL !== undefined ? import.meta.env.VITE_API_URL : 'http://localhost:8080';

const WS_BASE = /^https?:\/\//.test(API_BASE)
  ? API_BASE.replace(/^http/, 'ws')
  : `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}`;

const generarUUID = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

const IconMark = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M7 5 L15 12 L7 19" />
    <line x1="16" y1="19" x2="20" y2="19" />
  </svg>
);

const IconResumen = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
    <rect x="4" y="4" width="16" height="16" rx="2" />
    <line x1="8" y1="9" x2="16" y2="9" />
    <line x1="8" y1="13" x2="16" y2="13" />
    <line x1="8" y1="17" x2="12" y2="17" />
  </svg>
);

const IconCaja = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 3 v10" />
    <path d="M8 9 l4 4 4-4" />
    <path d="M4 15 v4 a1 1 0 0 0 1 1 h14 a1 1 0 0 0 1-1 v-4" />
  </svg>
);

const IconTransfer = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
    <path d="M4 8 h13" />
    <path d="M13 4 l4 4 -4 4" />
    <path d="M20 16 H7" />
    <path d="M11 12 l-4 4 4 4" />
  </svg>
);

const IconHistorial = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="8" />
    <path d="M12 8 v4 l3 2" />
  </svg>
);

const IconLogout = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 4 H6 a2 2 0 0 0 -2 2 v12 a2 2 0 0 0 2 2 h3" />
    <path d="M15 16 l4-4-4-4" />
    <path d="M19 12 H9" />
  </svg>
);

const IconPulse = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
    <path d="M3 12 h4 l2 -6 4 12 2 -6 h6" />
  </svg>
);

const IconCheck = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="9" />
    <path d="M8.5 12.5 l2.5 2.5 5-5" />
  </svg>
);

const IconAlert = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="9" />
    <line x1="12" y1="8" x2="12" y2="13" />
    <line x1="12" y1="16" x2="12" y2="16.01" />
  </svg>
);

const NAV_ITEMS = [
  { key: null, label: 'Resumen de Cuenta', code: 'GET saldo', Icon: IconResumen },
  { key: 'CAJA', label: 'Depósitos y Retiros', code: 'POST /api/transaccion', Icon: IconCaja },
  { key: 'TRANSF', label: 'Transferencias', code: 'POST /api/transferencia', Icon: IconTransfer },
  { key: 'HIST', label: 'Historial de Movimientos', code: 'POST /api/historial', Icon: IconHistorial },
];

const TRACE_LINES = [
  { label: 'HTTP', body: 'tomcat     :8080', value: 'OK' },
  { label: 'SQL', body: 'BEGIN; ... COMMIT;', value: 'TX' },
  { label: 'DB', body: 'usuarios · movimiento · operacion', value: '' },
  { label: 'WS', body: '/ws?usuario=…', value: 'OPEN' },
  { label: 'SYS', body: 'ESTADO_SISTEMA', value: 'ONLINE' },
];

const DEMO_USERS = [
  { usuario: 'JGONZALE', password: 'demo1234' },
  { usuario: 'MRODRIGU', password: 'demo1234' },
  { usuario: 'LFERNAND', password: 'demo1234' },
  { usuario: 'SPEREYRA', password: 'demo1234' },
  { usuario: 'CSUAREZ', password: 'demo1234' },
];

const BotonProgreso = ({ tipo, accion, label, loadingLabel, className = 'primary-btn', type = 'submit', onClick }) => {
  const activo = accion?.tipo === tipo;
  const listo = activo && accion.done;
  return (
    <button type={type} className={className} disabled={activo} onClick={onClick}>
      {activo && <span className={`btn-progress ${listo ? 'is-done' : 'is-loading'}`} />}
      <span className="btn-label">
        {activo && (listo ? <IconCheck className="btn-check" /> : <span className="btn-spinner" aria-hidden="true" />)}
        {activo ? (listo ? '¡Listo!' : (loadingLabel || 'Procesando…')) : label}
      </span>
    </button>
  );
};

function App() {
  const [usuario, setUsuario] = useState('');
  const [password, setPassword] = useState('');
  const [mensaje, setMensaje] = useState('');
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  const tokenRef = useRef(null);
  const authHeader = () => (tokenRef.current ? { Authorization: `Bearer ${tokenRef.current}` } : {});
  const guardarToken = (token) => {
    tokenRef.current = token;
  };

  const [saldo, setSaldo] = useState(null);
  const [monto, setMonto] = useState('');
  const [destino, setDestino] = useState('');
  const [modoActivo, setModoActivo] = useState(null);
  const [tipoCaja, setTipoCaja] = useState('D');

  const [historial, setHistorial] = useState([]);
  const [fechaDesde, setFechaDesde] = useState('');
  const [fechaHasta, setFechaHasta] = useState('');
  const [pagina, setPagina] = useState(1);
  const [hayMas, setHayMas] = useState(false);

  const [comprobante, setComprobante] = useState(null);

  const [llaveIdempotencia, setLlaveIdempotencia] = useState(generarUUID());
  const [notificacion, setNotificacion] = useState(null);
  const [coreTrace, setCoreTrace] = useState(null);
  const [accion, setAccion] = useState(null);

  useEffect(() => {
    setLlaveIdempotencia(generarUUID());
  }, [destino, monto]);

  const mostrarNotificacion = (texto, tipo) => {
    setNotificacion({ texto, tipo });
    setTimeout(() => setNotificacion(null), 4000);
  };

  const registrarTrace = (ruta, estado, ms) => {
    setCoreTrace({ ruta, estado, ms, hora: new Date().toLocaleTimeString() });
  };

  const manejar401 = (err) => {
    if (err.response?.status !== 401) return false;
    handleLogout();
    mostrarNotificacion('Tu sesión expiró. Iniciá sesión de nuevo.', 'error');
    return true;
  };

  const iniciarProgreso = (tipo) => {
    setAccion({ tipo, done: false });
  };

  const finalizarProgreso = (exito) => {
    if (!exito) {
      setAccion(null);
      return;
    }
    setAccion((prev) => (prev ? { ...prev, done: true } : prev));
    setTimeout(() => setAccion(null), 420);
  };

  useEffect(() => {
    if (!isLoggedIn || !tokenRef.current) return;

    const socket = new WebSocket(`${WS_BASE}/ws?token=${encodeURIComponent(tokenRef.current)}`);

    socket.onmessage = (event) => {
      try {
        const { evento, payload } = JSON.parse(event.data);
        if (evento === 'notificacion_transferencia') {
          console.log('[WebSocket] Transferencia recibida:', payload);
          mostrarNotificacion(`¡Recibiste $ ${payload.monto} de ${payload.origen} (${new Date().toLocaleTimeString()})!`, 'exito');
          ejecutarTransaccionSimple('C', 0);
        }
      } catch {
        console.error('[WebSocket] mensaje con formato inesperado');
      }
    };

    return () => socket.close();
  }, [isLoggedIn]);

  const formatMoneda = (valor) => {
    if (valor === null || valor === undefined) return '---';
    const numero = parseFloat(valor);
    if (isNaN(numero)) return '---';

    return new Intl.NumberFormat('es-AR', {
      style: 'decimal',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(numero);
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    const t0 = performance.now();
    iniciarProgreso('login');
    let exito = false;
    try {
      const res = await axios.post(`${API_BASE}/api/login`, { usuario, password });
      registrarTrace('POST /api/login', res.data.status === 'SUCCESS' ? 'OK' : 'ERR', Math.round(performance.now() - t0));
      if (res.data.status === 'SUCCESS') {
        exito = true;
        guardarToken(res.data.token);
        setIsLoggedIn(true);
        setMensaje('');
        ejecutarTransaccionSimple('C', 0);
      } else {
        setMensaje(res.data.message || 'No se pudo iniciar sesión');
      }
    } catch (err) {
      registrarTrace('POST /api/login', 'ERR', Math.round(performance.now() - t0));
      setMensaje(err.response?.data?.message || 'Error de conexión');
    } finally {
      finalizarProgreso(exito);
    }
  };

  const ejecutarTransaccionSimple = async (operacion, montoOperar) => {
    const t0 = performance.now();
    const mostrarCarga = operacion !== 'C';
    if (mostrarCarga) iniciarProgreso('caja');
    let exito = false;
    try {
      const res = await axios.post(`${API_BASE}/api/transaccion`, {
        operacion: operacion, monto: montoOperar
      }, { headers: authHeader() });
      registrarTrace('POST /api/transaccion', res.data.status === 'SUCCESS' ? 'OK' : 'ERR', Math.round(performance.now() - t0));
      if (res.data.status === 'SUCCESS') {
        exito = true;
        setSaldo(res.data.saldo);
        setModoActivo(null);
        setMonto('');
        if (operacion !== 'C') mostrarNotificacion(res.data.message, 'exito');
      }
    } catch (err) {
      registrarTrace('POST /api/transaccion', 'ERR', Math.round(performance.now() - t0));
      if (!manejar401(err) && operacion !== 'C') {
        mostrarNotificacion(err.response?.data?.message || 'Error en la operación', 'error');
      }
    } finally {
      if (mostrarCarga) finalizarProgreso(exito);
    }
  };

  const ejecutarTransferencia = async (e) => {
    e.preventDefault();
    const t0 = performance.now();
    iniciarProgreso('transferencia');
    let exito = false;

    try {
      const res = await axios.post(`${API_BASE}/api/transferencia`, {
        usuarioDestino: destino,
        monto: monto
      }, {
        headers: { ...authHeader(), 'x-idempotency-key': llaveIdempotencia }
      });

      registrarTrace('POST /api/transferencia', res.data.status === 'SUCCESS' ? 'OK' : 'ERR', Math.round(performance.now() - t0));

      if (res.data.status === 'SUCCESS') {
        exito = true;
        setSaldo(res.data.saldo_restante);

        setComprobante({
          destino: destino,
          montoOriginal: monto,
          auditoria: res.data.auditoria,
          desglose: res.data.desglose
        });
        setModoActivo('COMPROBANTE');

        mostrarNotificacion(res.data.message, 'exito');
        setLlaveIdempotencia(generarUUID());
      }
    } catch (err) {
      registrarTrace('POST /api/transferencia', 'ERR', Math.round(performance.now() - t0));
      if (!manejar401(err)) {
        mostrarNotificacion(err.response?.data?.message || 'Error en la transferencia', 'error');

        if (err.response && (err.response.status === 400 || err.response.status === 503)) {
          setLlaveIdempotencia(generarUUID());
        }
      }
    } finally {
      finalizarProgreso(exito);
    }
  };

  const cargarHistorial = async (pag) => {
    const t0 = performance.now();
    iniciarProgreso('historial');
    let exito = false;
    try {
      const res = await axios.post(`${API_BASE}/api/historial`, {
        fechaDesde, fechaHasta, pagina: pag
      }, { headers: authHeader() });

      registrarTrace('POST /api/historial', res.data.status === 'SUCCESS' ? 'OK' : 'ERR', Math.round(performance.now() - t0));

      if (res.data.status === 'SUCCESS') {
        exito = true;
        const movs = res.data.movimientos || [];
        setHistorial(movs);
        setPagina(pag);
        setHayMas(movs.length === 10);
        setModoActivo('HIST');
      } else {
        mostrarNotificacion('El servidor no devolvió estado SUCCESS', 'error');
      }
    } catch (err) {
      registrarTrace('POST /api/historial', 'ERR', Math.round(performance.now() - t0));
      if (!manejar401(err)) {
        mostrarNotificacion('Fallo crítico al cargar historial', 'error');
      }
    } finally {
      finalizarProgreso(exito);
    }
  };

  const verHistorial = () => {
    setFechaDesde('');
    setFechaHasta('');
    cargarHistorial(1);
  };

  const traducirOperacion = (tipo, contraparte) => {
    if (tipo === 'D') return 'Depósito';
    if (tipo === 'R') return 'Retiro';
    if (tipo === 'TS') return `Transf. a ${contraparte}`;
    if (tipo === 'TR') return `Transf. de ${contraparte}`;
    if (tipo === 'Y') return 'Rendimiento';
    return 'Movimiento';
  };

  const handleLogout = () => {
    tokenRef.current = null;
    setIsLoggedIn(false); setPassword(''); setSaldo(null); setModoActivo(null); setComprobante(null);
  };

  const resetearVista = (vista) => {
    setModoActivo(vista);
    setComprobante(null);
    setMonto('');
    setDestino('');
  };

  if (isLoggedIn) {
    return (
      <div className="dashboard-layout">

        {notificacion && (
          <div className={`notification-toast toast-${notificacion.tipo}`}>
            {notificacion.tipo === 'error' && <IconAlert className="toast-icon" />}
            {notificacion.tipo === 'exito' && <IconCheck className="toast-icon" />}
            <span className="toast-text">{notificacion.texto}</span>
          </div>
        )}

        <aside className="sidebar">
          <div className="sidebar-brand">
            <div className="brand-mark">
              <IconMark className="brand-mark-icon" />
              <span className="brand-mark-text">COREBANK</span>
              <span className="brand-variant-tag">JAVA</span>
            </div>
          </div>
          <nav className="sidebar-nav">
            {NAV_ITEMS.map((item) => (
              <button
                key={item.label}
                className={`nav-item ${modoActivo === item.key ? 'active' : ''}`}
                onClick={() => (item.key === 'HIST' ? verHistorial() : resetearVista(item.key))}
              >
                <item.Icon className="nav-item-icon" />
                <span className="nav-item-text">
                  <span className="nav-item-label">{item.label}</span>
                  <span className="nav-item-code">{item.code}</span>
                </span>
              </button>
            ))}
          </nav>
          <button className="sidebar-logout" onClick={handleLogout}>
            <IconLogout />
            <span>Cerrar sesión</span>
          </button>
        </aside>

        <main className="main-content">
          <header className="topbar">
            <div className="topbar-user-info">
              <p className="greeting">Bienvenido/a</p>
              <h3 className="username">{usuario.toUpperCase()}</h3>
            </div>
            <div className="balance-badge" onClick={() => ejecutarTransaccionSimple('C', 0)}>
              <IconPulse />
              <span>Saldo disponible</span>
              <strong>{saldo !== null ? `$ ${formatMoneda(saldo)}` : '---'}</strong>
            </div>
            <button className="topbar-logout" onClick={handleLogout} aria-label="Cerrar sesión">
              <IconLogout />
            </button>
          </header>

          {coreTrace && (
            <div className="core-trace-bar">
              <span className="core-trace-tag mono">API&gt;</span>
              <span className="core-trace-line">
                <span className="pgm">{coreTrace.ruta}</span> → postgres · {coreTrace.hora}
                <span className="blink-cursor" />
              </span>
              <span className={`core-trace-state ${coreTrace.estado === 'OK' ? 'ok' : 'err'}`}>
                {coreTrace.estado} · {coreTrace.ms}ms
              </span>
            </div>
          )}

          <div className="workspace">
            {modoActivo === null && (
              <div className="welcome-panel">
                <h1 className="welcome-title">Tu saldo vive en Postgres, no en caché.</h1>
                <p className="welcome-subtitle">
                  Lo que ves en pantalla es usuarios.saldo_actual, una caché de lectura. La verdad
                  contable vive en el ledger de movimiento y se audita cada noche. Elegí una
                  operación en el menú lateral para empezar.
                </p>
                <div className="quick-stats">
                  <div className="stat-card">
                    <h4>Estado del sistema</h4>
                    <span className="status-badge">
                      <span className="status-dot" /> Online · Postgres
                    </span>
                  </div>
                  <div className="stat-card">
                    <h4>Última conexión</h4>
                    <span className="mono">{new Date().toLocaleDateString()}</span>
                  </div>
                </div>
              </div>
            )}

            {modoActivo === 'CAJA' && (
              <div className="form-panel">
                <h2>Operaciones de Caja</h2>
                <p className="panel-meta">POST /api/transaccion · postgres</p>
                <form onSubmit={(e) => { e.preventDefault(); ejecutarTransaccionSimple(tipoCaja, monto); }}>
                  <div className="input-group">
                    <label>Tipo de Operación</label>
                    <select value={tipoCaja} onChange={(e) => setTipoCaja(e.target.value)}>
                      <option value="D">Depositar Efectivo</option>
                      <option value="R">Retirar Efectivo</option>
                    </select>
                  </div>
                  <div className="input-group">
                    <label>Monto de la operación</label>
                    <input type="number" step="0.01" placeholder="$ 0.00" value={monto} onChange={(e) => setMonto(e.target.value)} required />
                  </div>
                  <BotonProgreso tipo="caja" accion={accion} label="Confirmar Operación" loadingLabel="Procesando en Postgres…" />
                </form>
              </div>
            )}

            {modoActivo === 'TRANSF' && (
              <div className="form-panel">
                <h2>Transferencia a Terceros</h2>
                <p className="panel-meta">POST /api/transferencia · postgres</p>
                <form onSubmit={ejecutarTransferencia}>
                  <div className="input-group">
                    <label>Destino (Alias Interno o CVU)</label>
                    <input type="text" value={destino} onChange={(e) => setDestino(e.target.value)} required />
                    <div className="destino-sugerencias">
                      <span className="destino-sugerencias-label">Sugerencias:</span>
                      {DEMO_USERS.filter((u) => u.usuario !== usuario.toUpperCase()).map((u) => (
                        <button
                          type="button"
                          key={u.usuario}
                          className="destino-chip mono"
                          onClick={() => setDestino(u.usuario)}
                        >
                          {u.usuario}
                        </button>
                      ))}
                    </div>
                  </div>
                  <div className="input-group">
                    <label>Monto a transferir</label>
                    <input type="number" step="0.01" placeholder="$ 0.00" value={monto} onChange={(e) => setMonto(e.target.value)} required />
                  </div>
                  <BotonProgreso tipo="transferencia" accion={accion} label="Enviar Transferencia" loadingLabel="Procesando transferencia…" />
                </form>
              </div>
            )}

            {modoActivo === 'COMPROBANTE' && comprobante && (
              <div className="form-panel receipt-panel">
                <div className="punch-strip" />
                <div className="receipt-head">
                  <IconCheck />
                  <div>
                    <h2>Transferencia realizada</h2>
                    <p>POST /api/transferencia · postgres</p>
                  </div>
                </div>

                <div className="receipt-body">
                  <div className="receipt-row">
                    <span className="receipt-label">Destinatario</span>
                    <span className="receipt-value">{comprobante.destino}</span>
                  </div>
                  <div className="receipt-amount">$ {formatMoneda(comprobante.montoOriginal)}</div>

                  <div className="receipt-section-label">Trazabilidad</div>
                  <div className="receipt-slip">
                    <div className="receipt-row">
                      <span className="receipt-label">ID operación</span>
                      <span className="receipt-value">{comprobante.auditoria.id_operacion}</span>
                    </div>
                    <div className="receipt-row">
                      <span className="receipt-label">ID COELSA</span>
                      <span className="receipt-value">{comprobante.auditoria.id_coelsa}</span>
                    </div>
                    <div className="receipt-row">
                      <span className="receipt-label">CAE AFIP</span>
                      <span className="receipt-value">{comprobante.auditoria.factura_afip}</span>
                    </div>
                  </div>

                  {comprobante.desglose && comprobante.desglose.comision_fintech > 0 && (
                    <>
                      <div className="receipt-section-label">Cargos</div>
                      <div className="receipt-slip">
                        <div className="receipt-row">
                          <span className="receipt-label">Comisión por servicio</span>
                          <span className="receipt-value">$ {formatMoneda(comprobante.desglose.comision_fintech)}</span>
                        </div>
                        <div className="receipt-row">
                          <span className="receipt-label">IVA (21%)</span>
                          <span className="receipt-value">$ {formatMoneda(comprobante.desglose.iva_recaudado)}</span>
                        </div>
                      </div>
                    </>
                  )}
                </div>

                <div className="receipt-actions">
                  <button onClick={() => resetearVista('TRANSF')} className="secondary-btn">
                    Hacer otra transferencia
                  </button>
                </div>
              </div>
            )}

            {modoActivo === 'HIST' && (
              <div className="table-panel">
                <h2>Historial de Movimientos</h2>
                <p className="panel-meta">POST /api/historial · postgres</p>

                <div className="filters-bar">
                  <div style={{ flex: 1 }}>
                    <label>Desde Fecha:</label>
                    <input type="date" value={fechaDesde} onChange={(e) => setFechaDesde(e.target.value)} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <label>Hasta Fecha:</label>
                    <input type="date" value={fechaHasta} onChange={(e) => setFechaHasta(e.target.value)} />
                  </div>
                  <BotonProgreso
                    type="button"
                    tipo="historial"
                    accion={accion}
                    label="Filtrar"
                    loadingLabel="Buscando…"
                    className="primary-btn filter-btn"
                    onClick={() => cargarHistorial(1)}
                  />
                </div>

                {historial.length === 0 ? (
                  <p className="no-data">No se registran movimientos en este período.</p>
                ) : (
                  <>
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Fecha</th>
                          <th>Descripción</th>
                          <th style={{ textAlign: 'right' }}>Importe</th>
                        </tr>
                      </thead>
                      <tbody>
                        {historial.map((mov, index) => {
                          const numero = parseFloat(mov.monto);
                          const esCredito = !isNaN(numero) && numero >= 0;
                          const color = esCredito ? '#6fe7c4' : '#e2725b';
                          return (
                            <tr key={index}>
                              <td className="mono-cell" data-label="Fecha">{mov.fecha}</td>
                              <td data-label="Descripción">{traducirOperacion(mov.tipo, mov.contraparte)}</td>
                              <td className="mono-cell" data-label="Importe" style={{ textAlign: 'right', fontWeight: 'bold', color }}>
                                {esCredito ? '+' : '-'} $ {formatMoneda(Math.abs(numero))}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>

                    <div className="pagination-bar">
                      <button
                        onClick={() => cargarHistorial(pagina - 1)}
                        disabled={pagina === 1 || accion?.tipo === 'historial'}
                        className={pagina === 1 ? 'page-btn-disabled' : 'page-btn'}
                      >
                        ← Anterior
                      </button>
                      <span className="page-indicator">Página {pagina}</span>
                      <button
                        onClick={() => cargarHistorial(pagina + 1)}
                        disabled={!hayMas || accion?.tipo === 'historial'}
                        className={!hayMas ? 'page-btn-disabled' : 'page-btn'}
                      >
                        Siguiente →
                      </button>
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="login-layout">
      <div className="login-brand-panel">
        <div className="login-brand-inner">
          <div className="brand-mark">
            <IconMark className="brand-mark-icon" />
            <span className="brand-mark-text">COREBANK</span>
            <span className="brand-variant-tag">JAVA</span>
          </div>
          <h1 className="login-hero-title">Tu banco corre <em>sobre Java y Postgres</em>.</h1>
          <p className="login-hero-sub">
            Cada operación abre una transacción SQL, mueve el ledger y confirma en milisegundos.
          </p>
          <div className="login-status-row">
            <span className="status-dot" /> ESTADO_SISTEMA: ONLINE
          </div>
          <div className="trace-ticker" aria-hidden="true">
            {TRACE_LINES.map((line) => (
              <div className="trace-line mono" key={line.label}>
                {line.label}&nbsp;&nbsp;{line.body} {line.value && <>...... <span>{line.value}</span></>}
              </div>
            ))}
          </div>
        </div>
        <div className="punch-strip" />
      </div>

      <div className="login-form-panel">
        <div className="login-card">
          <p className="login-card-eyebrow">Portal de acceso</p>
          <h2>Ingresá a tu cuenta</h2>
          <p className="login-card-sub">Tu usuario y clave se validan con bcrypt contra la tabla usuarios.</p>
          <form onSubmit={handleLogin} className="login-form">
            <div className="input-group">
              <input type="text" aria-label="Usuario" placeholder="Usuario" value={usuario} onChange={(e) => setUsuario(e.target.value)} required />
            </div>
            <div className="input-group">
              <input type="password" aria-label="Contraseña" placeholder="Contraseña" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </div>
            <BotonProgreso tipo="login" accion={accion} label="Ingresar al sistema" loadingLabel="Verificando credenciales…" />
          </form>
          {mensaje && (
            <div className="error-message">
              <IconAlert className="toast-icon" /> {mensaje}
            </div>
          )}

          <div className="demo-creds">
            <p className="demo-creds-title">Entorno de demostración — datos ficticios. Elegí un usuario:</p>
            <div className="demo-creds-list">
              {DEMO_USERS.map((u) => (
                <button
                  type="button"
                  key={u.usuario}
                  className="demo-creds-row"
                  onClick={() => { setUsuario(u.usuario); setPassword(u.password); }}
                >
                  <span className="demo-creds-user mono">{u.usuario}</span>
                  <span className="demo-creds-pass mono">{u.password}</span>
                  <span className="demo-creds-hint">Usar</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;
