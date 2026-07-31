import { useEffect, useState } from "react";
import { Gift, Send, Users, Smartphone, Activity, AlertTriangle } from "lucide-react";
import {
  clientesDashboardService,
  AtivosResponse,
  OportunidadesResponse,
  ClienteAppAtivoResponse,
  TokenOrfaoResponse,
} from "@/services/clientesDashboardService";

function Card({
  title,
  value,
  icon: Icon,
  color = "#6B3E26",
  detail,
}: {
  title: string;
  value: string;
  icon: any;
  color?: string;
  detail?: string;
}) {
  return (
    <div className="card">
      <div className="card-icon" style={{ backgroundColor: color }}>
        <Icon size={16} color="#fff" />
      </div>
      <div className="card-title">{title}</div>
      {detail && <div className="card-detail">{detail}</div>}
      <div className="card-value">{value}</div>
    </div>
  );
}

export default function ClientesDashboard() {
  const [ativos, setAtivos] = useState<AtivosResponse | null>(null);
  const [oportunidades, setOportunidades] = useState<OportunidadesResponse>({
    novosComApp: [],
    inativosApp: [],
    tokensOrfaos: [],
  });
  const [clientesApp, setClientesApp] = useState<ClienteAppAtivoResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const totalClientes = ativos?.totalClientes ?? 0;
  const novos30 = ativos?.novosPeriodo ?? 0;
  const appAtivos = ativos?.appAtivos ?? 0;
  const tokensOrfaosCount = ativos?.tokensOrfaos ?? 0;
  const ativos7d = ativos?.ativos7d ?? 0;
  const adocao = ativos?.adocaoPercentual ?? 0;

  const novosComApp = oportunidades.novosComApp || [];
  const inativosApp = oportunidades.inativosApp || [];
  const tokensOrfaosList: TokenOrfaoResponse[] = oportunidades.tokensOrfaos || [];

  const [selectedClienteId, setSelectedClienteId] = useState<number>(0);
  const [statusMsg, setStatusMsg] = useState<string | null>(null);
  const [giftNote, setGiftNote] = useState("Brinde de boas-vindas");

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const [ativosRes, appAtivosRes, oportunidadesRes] = await Promise.all([
          clientesDashboardService.getAtivos(30),
          clientesDashboardService.getAppAtivos(),
          clientesDashboardService.getOportunidades(30),
        ]);
        setAtivos(ativosRes);
        setClientesApp(appAtivosRes);
        setOportunidades(oportunidadesRes);
        if (appAtivosRes.length > 0) {
          setSelectedClienteId(appAtivosRes[0].id);
        }
      } catch (e) {
        console.error("Erro ao carregar dashboard de clientes", e);
        setError("Não foi possível carregar os dados agora.");
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const formatDate = (dateStr?: string | null) => {
    if (!dateStr) return "—";
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return "—";
    return new Intl.DateTimeFormat("pt-BR").format(d);
  };

  const handleSendGift = () => {
    const cliente = clientesApp.find((c) => c.id === selectedClienteId);
    if (!cliente) return;
    clientesDashboardService
      .enviarBrinde({ userId: cliente.id, mensagem: giftNote })
      .then((res) => {
        setStatusMsg(res.detalhes || `Brinde/voucher enviado para ${cliente.nome} (${cliente.email}).`);
        setTimeout(() => setStatusMsg(null), 3500);
      })
      .catch(() => {
        setStatusMsg("Não foi possível enviar o brinde agora.");
        setTimeout(() => setStatusMsg(null), 3500);
      });
  };

  return (
    <div className="clientes-dashboard">
      <header className="header">
        <div>
          <h1>Clientes</h1>
          <p>Painel rápido para visão da base e envio de brinde/voucher 1:1.</p>
        </div>
      </header>

      <section className="kpis">
        <Card title="Clientes cadastrados" value={totalClientes.toString()} icon={Users} color="#6B3E26" />
        <Card title="Novos (30d)" value={novos30.toString()} icon={Activity} color="#B5854C" detail="Cadastro recente" />
        <Card title="Clientes com app" value={appAtivos.toString()} icon={Smartphone} color="#8B7355" detail={`Adoção: ${adocao}%`} />
        <Card title="Ativos (7d)" value={ativos7d.toString()} icon={Activity} color="#2E7D32" detail="Último acesso" />
        <Card title="Tokens órfãos" value={tokensOrfaosCount.toString()} icon={AlertTriangle} color="#D65A31" detail="Sem user_id" />
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <h2>Enviar brinde/voucher</h2>
            <p>Selecione um cliente e envie uma recompensa imediata.</p>
          </div>
        </div>
        {loading && <div className="muted">Carregando...</div>}
        {error && <div className="error">{error}</div>}
        <div className="form-grid">
          <label className="field">
            <span>Cliente</span>
            <select value={selectedClienteId} onChange={(e) => setSelectedClienteId(Number(e.target.value))}>
              {clientesApp.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nome} — {c.email}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Mensagem</span>
            <input value={giftNote} onChange={(e) => setGiftNote(e.target.value)} placeholder="Ex.: Brinde de boas-vindas" />
          </label>
          <div className="actions">
            <button onClick={handleSendGift}>
              <Send size={16} />
              Enviar
            </button>
            {statusMsg && <div className="status">{statusMsg}</div>}
          </div>
        </div>
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <h2>Oportunidades rápidas</h2>
            <p>Listas prontas para ação imediata.</p>
          </div>
        </div>
        <div className="op-grid">
          <div className="op-card">
            <div className="op-title">Boas-vindas (novos + app)</div>
            <div className="op-count">{novosComApp.length} clientes</div>
            <ul>
              {novosComApp.length === 0 && <li className="muted">Sem clientes no período</li>}
              {novosComApp.map((c) => (
                <li key={c.id}>
                  <Gift size={14} /> {c.nome}
                </li>
              ))}
            </ul>
          </div>
          <div className="op-card">
            <div className="op-title">Reengajar (inativos no app)</div>
            <div className="op-count">{inativosApp.length} clientes</div>
            <ul>
              {inativosApp.length === 0 && <li className="muted">Sem inativos detectados</li>}
              {inativosApp.map((c) => (
                <li key={c.id}>
                  <AlertTriangle size={14} /> {c.nome}
                </li>
              ))}
            </ul>
          </div>
          <div className="op-card">
            <div className="op-title">Tokens órfãos</div>
            <div className="op-count">{tokensOrfaosCount} tokens</div>
            <ul>
              {tokensOrfaosList.length === 0 && <li className="muted">Nenhum token órfão</li>}
              {tokensOrfaosList.map((t, idx) => (
                <li key={idx}>
                  <AlertTriangle size={14} /> {t.token}
                </li>
              ))}
            </ul>
          </div>
        </div>
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <h2>Clientes com app ativo</h2>
            <p>Último acesso, contato e ação rápida de brinde.</p>
          </div>
        </div>
        {loading && <div className="muted">Carregando clientes...</div>}
        <div className="table">
          <div className="table-head">
            <span>Cliente</span>
            <span>Contato</span>
            <span>Pontos</span>
            <span>Último acesso</span>
            <span>Tokens</span>
            <span></span>
          </div>
          {clientesApp.map((c) => (
            <div className="table-row" key={c.id}>
              <span>{c.nome}</span>
              <span>{c.email}{c.telefone ? ` • ${c.telefone}` : ""}</span>
              <span className="pill">{c.pontos} pts</span>
              <span>{formatDate(c.lastSeenAt)}</span>
              <span>{c.tokensAtivos}</span>
              <span>
                <button
                  className="ghost"
                  onClick={() => {
                    clientesDashboardService
                      .enviarBrinde({ userId: c.id, mensagem: giftNote })
                      .then((res) => setStatusMsg(res.detalhes || `Brinde enviado para ${c.nome}.`))
                      .catch(() => setStatusMsg("Não foi possível enviar o brinde agora."));
                    setTimeout(() => setStatusMsg(null), 3500);
                  }}
                >
                  <Gift size={14} /> Enviar
                </button>
              </span>
            </div>
          ))}
          {!loading && clientesApp.length === 0 && <div className="muted">Nenhum cliente ativo encontrado.</div>}
        </div>
      </section>

      <style>{`
        .clientes-dashboard {
          color: #2A1F1B;
          display: flex;
          flex-direction: column;
          gap: 16px;
          padding-bottom: 24px;
        }
        .header h1 {
          margin: 0;
          font-size: 24px;
          font-weight: 800;
          color: #6B3E26;
          letter-spacing: 0.04em;
        }
        .header p {
          margin: 4px 0 0;
          color: #8B7355;
        }
        .kpis {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
          gap: 12px;
        }
        .card {
          background: #fff;
          border: 1px solid #D7B899;
          border-radius: 10px;
          padding: 12px;
          box-shadow: 0 2px 8px rgba(0,0,0,0.05);
          display: flex;
          flex-direction: column;
          gap: 6px;
        }
        .card-icon {
          width: 30px;
          height: 30px;
          border-radius: 8px;
          display: grid;
          place-items: center;
        }
        .card-title { font-weight: 700; font-size: 14px; color: #2A1F1B; }
        .card-detail { font-size: 12px; color: #8B7355; }
        .card-value { font-size: 26px; font-weight: 800; color: #6B3E26; }

        .panel {
          background: #fff;
          border: 1px solid #D7B899;
          border-radius: 10px;
          padding: 14px;
          box-shadow: 0 2px 8px rgba(0,0,0,0.05);
        }
        .panel-header h2 {
          margin: 0;
          font-size: 18px;
          font-weight: 800;
          color: #6B3E26;
          letter-spacing: 0.05em;
        }
        .panel-header p { margin: 4px 0 0; color: #8B7355; font-size: 13px; }

        .form-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
          gap: 12px;
          margin-top: 12px;
          align-items: end;
        }
        .field { display: flex; flex-direction: column; gap: 6px; }
        .field span { font-size: 12px; color: #8B7355; }
        select, input {
          border: 1px solid #D7B899;
          border-radius: 8px;
          padding: 10px 12px;
          font-size: 14px;
          color: #2A1F1B;
          background: #fff;
        }
        .actions { display: flex; flex-direction: column; gap: 6px; }
        button {
          background: linear-gradient(90deg, #6B3E26, #8B7355);
          color: #fff;
          border: none;
          border-radius: 8px;
          padding: 10px 14px;
          font-weight: 700;
          display: inline-flex;
          align-items: center;
          gap: 8px;
          cursor: pointer;
        }
        button.ghost {
          background: transparent;
          color: #6B3E26;
          border: 1px solid #D7B899;
          padding: 8px 10px;
        }
        button:hover { opacity: 0.95; }
        .status { font-size: 12px; color: #2E7D32; }
        .muted { color: #8B7355; font-size: 13px; }
        .error { color: #D65A31; font-size: 13px; }

        .op-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
          gap: 12px;
          margin-top: 12px;
        }
        .op-card {
          border: 1px dashed #D7B899;
          border-radius: 8px;
          padding: 12px;
          background: #FBF6F2;
        }
        .op-title { font-weight: 700; color: #6B3E26; margin-bottom: 4px; }
        .op-count { color: #8B7355; font-size: 12px; margin-bottom: 8px; }
        .op-card ul { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 6px; color: #2A1F1B; }
        .op-card li { display: flex; align-items: center; gap: 6px; font-size: 13px; }

        .table {
          display: flex;
          flex-direction: column;
          gap: 4px;
          margin-top: 12px;
        }
        .table-head, .table-row {
          display: grid;
          grid-template-columns: 1.2fr 1.2fr 0.6fr 1fr 0.5fr 0.7fr;
          gap: 8px;
          padding: 8px 0;
        }
        .table-head {
          border-bottom: 1px solid #D7B899;
          font-weight: 700;
          color: #6B3E26;
        }
        .table-row {
          border-bottom: 1px solid #F0E3D4;
          font-size: 14px;
          align-items: center;
        }
        .pill {
          display: inline-block;
          padding: 1px 4px;
          border-radius: 12px;
          background: #FBF6F2;
          border: 1px solid #D7B899;
          font-weight: 700;
          font-size: 12px;
          color: #6B3E26;
          width: fit-content;
        }

        @media (max-width: 768px) {
          .table-head, .table-row {
            grid-template-columns: 1fr 1fr;
            grid-template-areas:
              "cliente contato"
              "pontos ultimo"
              "tokens acao";
          }
          .table-head span:nth-child(1),
          .table-row span:nth-child(1) { grid-area: cliente; }
          .table-head span:nth-child(2),
          .table-row span:nth-child(2) { grid-area: contato; }
          .table-head span:nth-child(3),
          .table-row span:nth-child(3) { grid-area: pontos; }
          .table-head span:nth-child(4),
          .table-row span:nth-child(4) { grid-area: ultimo; }
          .table-head span:nth-child(5),
          .table-row span:nth-child(5) { grid-area: tokens; }
          .table-head span:nth-child(6),
          .table-row span:nth-child(6) { grid-area: acao; }
        }
      `}</style>
    </div>
  );
}
