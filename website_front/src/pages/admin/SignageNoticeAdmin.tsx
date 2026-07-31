import { useEffect, useState, FormEvent } from "react";
import { Plus, Pencil, Trash2, X } from "lucide-react";
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import signageApi from "@/services/signageApi";

type NoticePayload = {
  title: string;
  subtitle?: string;
  durationSeconds?: number;
};

type Notice = {
  id: string;
  title: string;
  created_at: string;
  media_url: string;
  render_hash?: string;
  payload: NoticePayload;
  type?: string;
};

export default function SignageNoticeAdmin() {
  const [notices, setNotices] = useState<Notice[]>([]);
  const [title, setTitle] = useState("");
  const [subtitle, setSubtitle] = useState("");
  const [durationSeconds, setDurationSeconds] = useState(6);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingMediaUrl, setEditingMediaUrl] = useState("");
  const [editingRenderHash, setEditingRenderHash] = useState<string | undefined>(undefined);
  const [showModal, setShowModal] = useState(false);
  const [mediaFile, setMediaFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchNotices = async () => {
    try {
      const response = await signageApi.get("/screens");
      setNotices(response.data);
    } catch (err) {
      console.error("Failed to load notices", err);
      setError("Não foi possível carregar os notices.");
    }
  };

  useEffect(() => {
    fetchNotices();
  }, []);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      let finalMediaUrl = editingMediaUrl;
      let finalRenderHash = editingRenderHash;

      if (mediaFile) {
        const formData = new FormData();
        formData.append("file", mediaFile);
        const uploadResponse = await signageApi.post("/screens/upload", formData, {
          headers: { "Content-Type": "multipart/form-data" },
        });
        finalMediaUrl = uploadResponse.data.mediaUrl;
        finalRenderHash = undefined;
      }

      const noticePayload = {
        type: "notice",
        title,
        payload: {
          title,
          subtitle,
          durationSeconds,
          tokens: {
            brandColor: "#1d4ed8",
            accentColor: "#ef4444",
            font: "Inter",
            layout: "notice-center",
            bgStyle: "solid",
          },
        },
        mediaUrl: finalMediaUrl,
        renderHash: finalRenderHash,
      };

      let response;
      if (editingId) {
        response = await signageApi.put(`/screens/${editingId}`, noticePayload);
      } else {
        response = await signageApi.post("/screens", { ...noticePayload, mediaUrl: finalMediaUrl || "", renderHash: finalRenderHash });
      }
      setNotices((prev) => {
        const filtered = prev.filter((notice) => notice.id !== response.data.id);
        return [response.data, ...filtered];
      });
      setTitle("");
      setSubtitle("");
      setDurationSeconds(6);
      setEditingId(null);
      setEditingMediaUrl("");
      setEditingRenderHash(undefined);
      setMediaFile(null);
      setShowModal(false);
    } catch (err) {
      console.error("Failed to save notice", err);
      setError("Erro ao salvar mídia.");
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    setError(null);
    try {
      await signageApi.delete(`/screens/${id}`);
      setNotices((prev) => prev.filter((notice) => notice.id !== id));
    } catch (err) {
      console.error("Failed to delete notice", err);
      setError("Erro ao excluir mídia.");
    }
  };

  const handleEdit = (notice: Notice) => {
    setTitle(notice.payload.title);
    setSubtitle(notice.payload.subtitle || "");
    setDurationSeconds(notice.payload.durationSeconds || 6);
    setEditingId(notice.id);
    setEditingMediaUrl(notice.media_url);
    setEditingRenderHash(notice.render_hash);
    setMediaFile(null);
    setShowModal(true);
  };

  const handleCreateOpen = () => {
    setTitle("");
    setSubtitle("");
    setDurationSeconds(6);
    setEditingId(null);
    setEditingMediaUrl("");
    setEditingRenderHash(undefined);
    setMediaFile(null);
    setShowModal(true);
  };

  const handleCloseModal = () => {
    if (loading) return;
    setShowModal(false);
    setMediaFile(null);
  };

  const resolveMediaUrl = (path?: string | null) => {
    if (!path) return "";
    if (path.startsWith("http://") || path.startsWith("https://")) {
      return path;
    }
    const base = (signageApi.defaults.baseURL || "").toString().replace(/\/+$/, "");
    return `${base}${path}`;
  };

  const formatDateTime = (value: string) => {
    const date = new Date(value);
    const pad = (num: number) => String(num).padStart(2, "0");
    const day = pad(date.getDate());
    const month = pad(date.getMonth() + 1);
    const year = date.getFullYear();
    const hours = pad(date.getHours());
    const minutes = pad(date.getMinutes());
    return `${day}/${month}/${year} ${hours}:${minutes}`;
  };

  return (
    <div className="page">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-display text-[#2A1F1B]">Digital Signage — Mídias</h1>
          <p className="text-sm text-[#8B7355]/70">Gerencie mídias manuais (custom) exibidas no player.</p>
        </div>
        <Button onClick={handleCreateOpen} className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90 font-medium">
          <Plus className="w-4 h-4 mr-2" />
          Adicionar mídia
        </Button>
      </div>
      {error && <p className="error">{error}</p>}
      <div className="notice-list">
        <div className="bg-white rounded-2xl shadow-xl border border-[#D7B899]/20 overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow className="border-b border-[#D7B899]/20 bg-[#D7B899]/5">
                <TableHead className="text-[#2A1F1B] font-medium">Título</TableHead>
                <TableHead className="text-[#2A1F1B] font-medium">Tipo</TableHead>
                <TableHead className="text-[#2A1F1B] font-medium">Criado em</TableHead>
                <TableHead className="text-[#2A1F1B] font-medium">Mídia</TableHead>
                <TableHead className="text-right text-[#2A1F1B] font-medium">Ações</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {notices
                .slice()
                .sort((a, b) => a.title.localeCompare(b.title, 'pt-BR', { sensitivity: 'base' }))
                .map((notice) => (
                <TableRow
                  key={notice.id}
                  className="hover:bg-[#D7B899]/5 transition-colors border-b border-[#D7B899]/10"
                >
                  <TableCell>
                    <div className="font-semibold text-[#2A1F1B]">{notice.title}</div>
                    <div className="text-xs text-[#8B7355]/70">{notice.id}</div>
                  </TableCell>
                  <TableCell className="text-[#8B7355]/80">
                    {(notice.type === "product" && "Produto") ||
                      (notice.type === "event" && "Evento") ||
                      "Custom"}
                  </TableCell>
                  <TableCell className="text-[#8B7355]/80">
                    {formatDateTime(notice.created_at)}
                  </TableCell>
                  <TableCell>
                    {notice.media_url ? (
                      <a
                        className="text-[#8B7355] hover:underline"
                        href={resolveMediaUrl(notice.media_url)}
                        target="_blank"
                        rel="noreferrer"
                      >
                        Abrir
                      </a>
                    ) : (
                      "—"
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="hover:bg-[#D7B899]/10"
                        onClick={() => handleEdit(notice)}
                      >
                        <Pencil className="w-4 h-4 text-[#D7B899]" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="hover:bg-red-500/10"
                        onClick={() => handleDelete(notice.id)}
                      >
                        <Trash2 className="w-4 h-4 text-[#D65A31]" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
              {notices.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} className="text-center text-[#8B7355]/70">
                    Nenhuma mídia encontrada.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </div>
      {showModal && (
        <div className="modal-backdrop" onClick={handleCloseModal}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h2>{editingId ? "Editar mídia" : "Nova mídia"}</h2>
              <button type="button" className="icon-button" onClick={handleCloseModal}>
                <X className="icon" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="modal-form">
              <label>
                Título
                <input
                  value={title}
                  required
                  onChange={(event) => setTitle(event.target.value)}
                  placeholder="Título da mídia"
                />
              </label>
              <label>
                Subtítulo
                <input
                  value={subtitle}
                  onChange={(event) => setSubtitle(event.target.value)}
                  placeholder="Subtítulo / descrição"
                />
              </label>
              <label>
                Duração (segundos)
                <input
                  type="number"
                  min={1}
                  value={durationSeconds}
                  onChange={(event) => setDurationSeconds(Number(event.target.value))}
                />
              </label>
              <label>
                Vídeo (MP4)
                <input
                  type="file"
                  accept="video/mp4"
                  onChange={(event) => setMediaFile(event.target.files?.[0] || null)}
                />
              </label>
              <div className="modal-actions">
                <Button
                  type="button"
                  variant="outline"
                  className="border-[#D7B899]/60 text-[#2A1F1B] hover:bg-[#D7B899]/10"
                  onClick={handleCloseModal}
                >
                  Cancelar
                </Button>
                <Button
                  type="submit"
                  className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90"
                  disabled={loading}
                >
                  {editingId ? (loading ? "Salvando..." : "Salvar") : loading ? "Criando..." : "Criar"}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
      <style>{`
        .error {
          color: #dc2626;
          margin-top: 1rem;
        }
        .modal-backdrop {
          position: fixed;
          inset: 0;
          background: rgba(42, 31, 27, 0.4);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 50;
        }
        .modal {
          background: #fff;
          border-radius: 14px;
          padding: 1.5rem;
          width: min(560px, 92vw);
          box-shadow: 0 16px 32px rgba(42, 31, 27, 0.2);
        }
        .modal-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 1rem;
        }
        .icon-button {
          background: transparent;
          border: none;
          padding: 0.25rem;
          border-radius: 6px;
          color: #8B7355;
          cursor: pointer;
        }
        .icon-button:hover {
          background: rgba(215, 184, 153, 0.25);
          color: #6B3E26;
        }
        .icon {
          width: 18px;
          height: 18px;
        }
        .modal-form {
          display: grid;
          gap: 0.75rem;
        }
        .modal-form label {
          display: flex;
          flex-direction: column;
          font-weight: 600;
          color: #2A1F1B;
        }
        .modal-form input {
          padding: 0.55rem 0.65rem;
          border-radius: 8px;
          border: 1px solid #D7B899;
          background: #fff;
          color: #2A1F1B;
          margin-top: 0.25rem;
        }
        .modal-actions {
          display: flex;
          justify-content: flex-end;
          gap: 0.5rem;
          margin-top: 0.5rem;
        }
      `}</style>
    </div>
  );
}
