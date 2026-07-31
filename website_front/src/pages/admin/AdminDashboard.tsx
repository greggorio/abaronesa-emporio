import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Users, Trophy, Calendar, DollarSign, Ban } from 'lucide-react';
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';

function KpiCard({
  title,
  value,
  icon: Icon,
  color,
  subtitle,
  onClick,
}: { title: string; value: number; icon: any; color?: string; subtitle?: string; onClick?: () => void }) {
  return (
    <div className={`kpi-card ${onClick ? 'cursor-pointer' : ''}`} onClick={onClick}>
      <div className={`kpi-badge ${color || 'badge-gray'}`}>
        <Icon size={16} />
      </div>
      <div className="kpi-title">{title}</div>
      {subtitle && <div className="kpi-sub">{subtitle}</div>}
      <div className="kpi-count">
        <span>{value}</span>
      </div>

      <style>{`
        .kpi-card {
          position: relative;
          background: white;
          border: 1px solid #D7B899; /* cafe-latte-suave */
          border-radius: 8px;
          padding: 14px 16px;
          min-height: 100px;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
          transition: all 0.2s ease;
          display: flex;
          flex-direction: column;
        }
        .kpi-card:hover {
          border-color: #6B3E26; /* cafe-espresso */
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        }
        .kpi-badge {
          width: 28px;
          height: 28px;
          border-radius: 6px;
          display: grid;
          place-items: center;
          margin-bottom: 8px;
          flex-shrink: 0;
        }
        .badge-gold { background: #D7B899; color: #6B3E26; } /* cafe-latte-suave background, cafe-espresso text */
        .badge-blue { background: #8B7355; color: #FBF6F2; } /* cafe-com-leite background, cafe-latte-claro text */
        .badge-green { background: #B5854C; color: #FBF6F2; } /* cafe-bronze background, cafe-latte-claro text */
        .badge-purple { background: #C67C48; color: #FBF6F2; } /* cafe-caramelo background, cafe-latte-claro text */
        .badge-gray { background: #D7B899; color: #2A1F1B; } /* cafe-latte-suave background, cafe-dark-roast text */
        .badge-red { background: #D65A31; color: #FBF6F2; } /* negative color from bakery palette */
        .kpi-title {
          color: #2A1F1B; /* cafe-dark-roast */
          font-weight: 600;
          margin-bottom: 4px;
          font-size: 14px;
          line-height: 1.3;
        }
        .kpi-sub {
          color: #8B7355; /* cafe-com-leite */
          font-size: 11px;
          line-height: 1.3;
          margin-bottom: 8px;
        }
        .kpi-count {
          font-weight: 700;
          font-size: 24px;
          color: #6B3E26; /* cafe-espresso */
          margin-top: auto;
        }
        .kpi-count > span {
          display: inline-block;
        }

        @media (min-width: 640px) {
          .kpi-card {
            padding: 16px 20px;
            min-height: 110px;
          }
          .kpi-badge {
            width: 32px;
            height: 32px;
            margin-bottom: 10px;
          }
          .kpi-title {
            font-size: 15px;
            margin-bottom: 6px;
          }
          .kpi-sub {
            font-size: 12px;
          }
          .kpi-count {
            font-size: 28px;
          }
        }
      `}</style>
    </div>
  );
}

export default function AdminDashboard() {
  const userName = useMemo(() => localStorage.getItem('user_nome') || 'Usuário', []);
  const primeiroNome = useMemo(() => userName.split(' ')[0], [userName]);
  const [canceladosHoje, setCanceladosHoje] = useState<{ totalItens: number; valorTotal: number }>({ totalItens: 0, valorTotal: 0 });
  const navigate = useNavigate();

  useEffect(() => {
    const loadKpiCancelados = async () => {
      try {
        const res = await axios.get<{ totalItens: number; valorTotal: number }>(`${apiConfig.erpBaseUrl}/api/admin/cancelamentos/hoje`);
        setCanceladosHoje(res.data || { totalItens: 0, valorTotal: 0 });
      } catch (e) {
        console.error('Falha ao carregar KPI de cancelamentos', e);
      }
    };
    loadKpiCancelados();
  }, []);

  return (
    <div className="admin-dashboard">
      {/* Saudação */}
      <div className="greeting">
        <h1 className="greet-title">
          Olá <span className="greet-name">{primeiroNome}</span>,
        </h1>
        <p className="greet-sub">Bem-vindo ao painel administrativo!</p>
      </div>

      {/* KPIs */}
      <div className="kpi-grid">
        <KpiCard
          title="Eventos Ativos"
          subtitle="Eventos em andamento"
          value={0}
          icon={Trophy}
          color="badge-blue"
        />
        <KpiCard
          title="Clientes Cadastrados"
          subtitle="Total de clientes"
          value={0}
          icon={Users}
          color="badge-green"
          onClick={() => navigate('/admin/clientes')}
        />
        <KpiCard
          title="Faturamento Hoje"
          subtitle="Receita do dia"
          value={0}
          icon={DollarSign}
          color="badge-purple"
        />
        <KpiCard
          title="Cancelado Hoje"
          subtitle="Itens cancelados"
          value={canceladosHoje.totalItens}
          icon={Ban}
          color="badge-red"
          onClick={() => navigate('/admin/cancelamentos')}
        />
      </div>

      {/* Placeholder para conteúdo futuro */}
      <section className="content-section">
        <div className="section-card">
          <div className="section-header">
            <div className="section-title">Dashboard em Desenvolvimento</div>
          </div>
          <div className="section-content">
            <p className="placeholder-text">
              Este painel será preenchido com métricas, gráficos e informações
              relevantes para gestão do Empório A Baronesa.
            </p>
          </div>
        </div>
      </section>

      <style>{`
        .admin-dashboard {
          color: #2A1F1B; /* cafe-dark-roast */
          padding: 0;
        }

        .greeting {
          margin-bottom: 20px;
        }

        .greet-title {
          font-size: 22px;
          font-weight: 800;
          color: #2A1F1B; /* cafe-dark-roast */
          margin: 0 0 6px 0;
          font-family: var(--font-display);
          letter-spacing: 0.05em;
          line-height: 1.2;
        }

        .greet-name {
          color: #6B3E26; /* cafe-espresso */
        }

        .greet-sub {
          color: #8B7355; /* cafe-com-leite */
          font-size: 14px;
          line-height: 1.4;
        }

        .kpi-grid {
          display: grid;
          grid-template-columns: repeat(1, 1fr);
          gap: 12px;
          margin-bottom: 20px;
        }

        .content-section {
          margin-top: 24px;
        }

        .section-card {
          border-radius: 8px;
          background: white;
          border: 1px solid #D7B899; /* cafe-latte-suave */
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
          padding: 16px;
          overflow: hidden;
        }

        .section-header {
          margin-bottom: 12px;
          padding-bottom: 10px;
          border-bottom: 1px solid #D7B899; /* cafe-latte-suave */
        }

        .section-title {
          font-size: 16px;
          font-weight: 700;
          color: #6B3E26; /* cafe-espresso */
          font-family: var(--font-display);
          letter-spacing: 0.1em;
        }

        .section-content {
          padding: 16px 0;
        }

        .placeholder-text {
          color: #8B7355; /* cafe-com-leite */
          font-size: 14px;
          line-height: 1.6;
          text-align: center;
        }

        /* Tablet */
        @media (min-width: 640px) {
          .greeting {
            margin-bottom: 24px;
          }

          .greet-title {
            font-size: 28px;
            margin-bottom: 8px;
          }

          .greet-sub {
            font-size: 15px;
          }

          .kpi-grid {
            grid-template-columns: repeat(2, 1fr);
            gap: 14px;
            margin-bottom: 28px;
          }

          .section-card {
            padding: 18px;
          }

          .section-title {
            font-size: 18px;
          }

          .placeholder-text {
            font-size: 15px;
          }
        }

        /* Desktop */
        @media (min-width: 1024px) {
          .greet-title {
            font-size: 32px;
          }

          .greet-sub {
            font-size: 16px;
          }

          .kpi-grid {
            grid-template-columns: repeat(4, 1fr);
            gap: 16px;
          }

          .section-card {
            padding: 20px;
          }

          .section-header {
            margin-bottom: 16px;
            padding-bottom: 12px;
          }

          .section-title {
            font-size: 20px;
          }

          .section-content {
            padding: 20px 0;
          }

          .placeholder-text {
            font-size: 16px;
          }
        }
      `}</style>
    </div>
  );
}
