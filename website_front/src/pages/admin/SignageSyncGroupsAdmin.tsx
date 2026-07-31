import { useEffect, useState, FormEvent } from "react";
import { Plus, Pencil, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useToast } from "@/components/ui/use-toast";
import signageApi from "@/services/signageApi";

type SyncGroup = {
  id: string;
  name: string;
  scheduleId?: string;
};

type Schedule = {
  id: string;
  name: string;
  timezone: string;
};

export default function SignageSyncGroupsAdmin() {
  const { toast } = useToast();
  const [syncGroups, setSyncGroups] = useState<SyncGroup[]>([]);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [modalError, setModalError] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<SyncGroup | null>(null);

  const fetchSyncGroups = async () => {
    try {
      const response = await signageApi.get("/sync-groups");
      const normalized = response.data.map((group: any) => ({
        id: group.id,
        name: group.name,
        scheduleId: group.scheduleId ?? group.schedule_id ?? "",
      }));
      setSyncGroups(normalized);
    } catch (err) {
      console.error(err);
      setError("Erro ao carregar sync groups.");
    }
  };

  const fetchSchedules = async () => {
    try {
      const response = await signageApi.get("/schedules");
      setSchedules(response.data);
    } catch (err) {
      console.error(err);
      setError("Erro ao carregar agendas.");
    }
  };

  useEffect(() => {
    fetchSyncGroups();
    fetchSchedules();
  }, []);

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setModalError(null);
    try {
      const exists = syncGroups.some(
        (group) => group.name.trim().toLowerCase() === name.trim().toLowerCase()
      );
      if (exists) {
        setModalError("Já existe um sync group com esse nome.");
        return;
      }
      await signageApi.post("/sync-groups", { name, scheduleId: null });
      setName("");
      setShowModal(false);
      setEditingId(null);
      fetchSyncGroups();
    } catch (err) {
      console.error(err);
      const status = (err as any)?.response?.status;
      if (status === 409) {
        setModalError("Já existe um sync group com esse nome.");
      } else {
        setModalError("Erro ao criar sync group.");
      }
    }
  };

  const handleOpenCreate = () => {
    setName("");
    setEditingId(null);
    setModalError(null);
    setShowModal(true);
  };

  const handleOpenEdit = (group: SyncGroup) => {
    setName(group.name);
    setEditingId(group.id);
    setModalError(null);
    setShowModal(true);
  };

  const handleUpdate = async (event: FormEvent) => {
    event.preventDefault();
    if (!editingId) return;
    setError(null);
    setModalError(null);
    try {
      const exists = syncGroups.some(
        (group) =>
          group.id !== editingId &&
          group.name.trim().toLowerCase() === name.trim().toLowerCase()
      );
      if (exists) {
        setModalError("Já existe um sync group com esse nome.");
        return;
      }
      const current = syncGroups.find((group) => group.id === editingId);
      await signageApi.put(`/sync-groups/${editingId}`, {
        name,
        scheduleId: current?.scheduleId || null,
      });
      setName("");
      setEditingId(null);
      setShowModal(false);
      fetchSyncGroups();
    } catch (err) {
      console.error(err);
      const status = (err as any)?.response?.status;
      if (status === 409) {
        setModalError("Já existe um sync group com esse nome.");
      } else {
        setModalError("Erro ao atualizar sync group.");
      }
    }
  };

  const handleDelete = async (id: string) => {
    setError(null);
    try {
      await signageApi.delete(`/sync-groups/${id}`);
      fetchSyncGroups();
    } catch (err) {
      console.error(err);
      const status = (err as any)?.response?.status;
      if (status === 409) {
        toast({
          title: "Não é possível excluir",
          description: "Há dispositivos vinculados a este sync group.",
          variant: "destructive",
        });
      } else {
        setError("Erro ao excluir sync group.");
      }
    }
  };

  const handleOpenDelete = (group: SyncGroup) => {
    setDeleteTarget(group);
    setShowDeleteModal(true);
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    await handleDelete(deleteTarget.id);
    setShowDeleteModal(false);
    setDeleteTarget(null);
  };

  const handleScheduleAssign = async (syncGroupId: string, scheduleId: string) => {
    setError(null);
    try {
      const current = syncGroups.find((group) => group.id === syncGroupId);
      await signageApi.put(`/sync-groups/${syncGroupId}`, {
        name: current?.name,
        scheduleId: scheduleId || null,
      });
      fetchSyncGroups();
    } catch (err) {
      console.error(err);
      setError("Erro ao associar agenda ao sync group.");
    }
  };

  return (
    <div className="signage-sync">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-display text-[#2A1F1B]">Digital Signage — Sync Groups</h1>
          <p className="text-sm text-[#8B7355]/70">
            Associe uma agenda (schedule) a cada grupo para sincronizar os devices.
          </p>
        </div>
        <Button
          onClick={handleOpenCreate}
          className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90 font-medium"
        >
          <Plus className="w-4 h-4 mr-2" />
          Adicionar sync group
        </Button>
      </div>

      {error && <p className="error">{error}</p>}

      <section className="table-card">
        <Table>
          <TableHeader>
            <TableRow className="border-b border-[#D7B899]/20 bg-[#D7B899]/5">
              <TableHead className="text-[#2A1F1B] font-medium">Nome</TableHead>
              <TableHead className="text-[#2A1F1B] font-medium">Agenda</TableHead>
              <TableHead className="text-right text-[#2A1F1B] font-medium">Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {syncGroups.map((group) => (
              <TableRow key={group.id} className="hover:bg-[#D7B899]/5 transition-colors">
                <TableCell className="text-[#2A1F1B] font-medium">{group.name}</TableCell>
                <TableCell>
                  <select
                    value={group.scheduleId || ""}
                    onChange={(event) => handleScheduleAssign(group.id, event.target.value)}
                  >
                    <option value="">Sem agenda</option>
                    {schedules.map((schedule) => (
                      <option key={schedule.id} value={schedule.id}>
                        {schedule.name}
                      </option>
                    ))}
                  </select>
                </TableCell>
                <TableCell className="text-right">
                  <div className="action-buttons">
                    <button type="button" className="icon-button" onClick={() => handleOpenEdit(group)} title="Editar">
                      <Pencil className="icon" />
                    </button>
                    <button type="button" className="icon-button delete" onClick={() => handleOpenDelete(group)} title="Excluir">
                      <Trash2 className="icon" />
                    </button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
            {syncGroups.length === 0 && (
              <TableRow>
                <TableCell colSpan={3} className="text-center text-[#8B7355]/70">
                  Nenhum sync group encontrado.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </section>

      {showModal && (
        <div className="modal-backdrop" onClick={() => setShowModal(false)}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingId ? "Editar sync group" : "Novo sync group"}</h3>
              <button type="button" className="icon-button" onClick={() => setShowModal(false)}>
                <X className="icon" />
              </button>
            </div>
            {modalError && <p className="modal-error">{modalError}</p>}
            <form onSubmit={editingId ? handleUpdate : handleCreate} className="modal-form">
              <label>
                Nome
                <input
                  placeholder="Nome do sync group"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  required
                />
              </label>
              <div className="modal-actions">
                <button type="button" className="secondary" onClick={() => setShowModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="primary">
                  {editingId ? "Salvar" : "Criar"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showDeleteModal && deleteTarget && (
        <div className="modal-backdrop" onClick={() => setShowDeleteModal(false)}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Excluir sync group</h3>
              <button type="button" className="icon-button" onClick={() => setShowDeleteModal(false)}>
                <X className="icon" />
              </button>
            </div>
            <p className="modal-text">
              Tem certeza que deseja excluir <strong>{deleteTarget.name}</strong>?
            </p>
            <div className="modal-actions">
              <button type="button" className="secondary" onClick={() => setShowDeleteModal(false)}>
                Cancelar
              </button>
              <button type="button" className="primary danger" onClick={handleConfirmDelete}>
                Excluir
              </button>
            </div>
          </div>
        </div>
      )}

      <style>{`
        .card {
          background: #fff;
          border-radius: 14px;
          padding: 1.25rem;
          box-shadow: 0 6px 18px rgba(42, 31, 27, 0.08);
          border: 1px solid #EADCCB;
          margin-bottom: 1rem;
        }
        .table-card {
          background: #fff;
          border-radius: 16px;
          box-shadow: 0 16px 32px rgba(42, 31, 27, 0.12);
          border: 1px solid rgba(215, 184, 153, 0.2);
          overflow: hidden;
          margin-bottom: 1rem;
        }
        .form-row {
          display: flex;
          gap: 0.75rem;
          flex-wrap: wrap;
        }
        input, select {
          padding: 0.55rem 0.65rem;
          border-radius: 8px;
          border: 1px solid #D7B899;
          background: #fff;
          color: #2A1F1B;
        }
        .primary {
          background: #D7B899;
          color: #2A1F1B;
          border: none;
          border-radius: 8px;
          padding: 0.6rem 1.25rem;
          font-weight: 600;
          cursor: pointer;
        }
        .actions {
          width: 120px;
          text-align: right;
        }
        .action-buttons {
          display: flex;
          justify-content: flex-end;
          gap: 0.4rem;
        }
        .icon-button {
          background: transparent;
          border: none;
          padding: 0.2rem;
          border-radius: 8px;
          color: #8B7355;
          cursor: pointer;
        }
        .icon-button:hover {
          color: #6B3E26;
          background: rgba(215, 184, 153, 0.25);
        }
        .icon-button.delete {
          color: #D65A31;
        }
        .icon-button.delete:hover {
          background: rgba(214, 90, 49, 0.12);
        }
        .icon {
          width: 18px;
          height: 18px;
        }
        .error {
          color: #D65A31;
          margin: 0.75rem 0;
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
          padding: 1.25rem;
          width: min(520px, 92vw);
          box-shadow: 0 16px 32px rgba(42, 31, 27, 0.2);
        }
        .modal-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 0.75rem;
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
        .modal-error {
          color: #D65A31;
          margin-bottom: 0.5rem;
        }
        .modal-actions {
          display: flex;
          justify-content: flex-end;
          gap: 0.5rem;
        }
        .secondary {
          padding: 0.6rem 1.2rem;
          border: 1px solid #D7B899;
          border-radius: 6px;
          background: #F5EDE6;
          color: #2A1F1B;
          font-weight: 600;
          cursor: pointer;
        }
        .modal-text {
          color: #2A1F1B;
          margin-bottom: 0.75rem;
        }
        .danger {
          background: #D65A31;
          color: #fff;
        }
      `}</style>
    </div>
  );
}
