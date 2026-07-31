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
import signageApi from "@/services/signageApi";

type Device = {
  id: string;
  name: string;
  location?: string;
  syncGroupId?: string;
  playlistId?: string;
};

type SyncGroup = {
  id: string;
  name: string;
};

type Playlist = {
  id: string;
  name: string;
};

export default function SignageDevicesAdmin() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [syncGroups, setSyncGroups] = useState<SyncGroup[]>([]);
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [name, setName] = useState("");
  const [location, setLocation] = useState("");
  const [selectedSync, setSelectedSync] = useState("");
  const [selectedPlaylist, setSelectedPlaylist] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Device | null>(null);

  const fetchDevices = async () => {
    try {
      const response = await signageApi.get("/devices");
      const normalized = response.data.map((device: any) => ({
        id: device.id,
        name: device.name,
        location: device.location,
        syncGroupId: device.syncGroupId ?? device.sync_group_id ?? "",
        playlistId: device.playlistId ?? device.playlist_id ?? "",
      }));
      setDevices(normalized);
    } catch (err) {
      console.error(err);
      setError("Não foi possível carregar dispositivos.");
    }
  };

  const fetchSyncGroups = async () => {
    try {
      const response = await signageApi.get("/sync-groups");
      setSyncGroups(response.data);
    } catch (err) {
      console.error(err);
      setError("Erro ao carregar sync groups.");
    }
  };

  const fetchPlaylists = async () => {
    try {
      const response = await signageApi.get("/playlists");
      setPlaylists(response.data);
      if (!selectedPlaylist && response.data.length > 0) {
        setSelectedPlaylist(response.data[0].id);
      }
    } catch (err) {
      console.error(err);
      setError("Erro ao carregar playlists.");
    }
  };

  useEffect(() => {
    fetchDevices();
    fetchSyncGroups();
    fetchPlaylists();
  }, []);

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    try {
      await signageApi.post("/devices", {
        name,
        location,
        syncGroupId: selectedSync || null,
        playlistId: selectedPlaylist || null,
      });
      setName("");
      setLocation("");
      setSelectedPlaylist("");
      setShowModal(false);
      setEditingId(null);
      fetchDevices();
    } catch (err) {
      console.error(err);
      setError("Erro ao criar dispositivo.");
    }
  };

  const handleOpenCreate = () => {
    setName("");
    setLocation("");
    setSelectedSync("");
    setSelectedPlaylist("");
    setEditingId(null);
    setShowModal(true);
  };

  const handleOpenEdit = (device: Device) => {
    setName(device.name);
    setLocation(device.location || "");
    setSelectedSync(device.syncGroupId || "");
    setSelectedPlaylist(device.playlistId || "");
    setEditingId(device.id);
    setShowModal(true);
  };

  const handleUpdate = async (event: FormEvent) => {
    event.preventDefault();
    if (!editingId) return;
    setError(null);
    try {
      await signageApi.put(`/devices/${editingId}`, {
        name,
        location,
        syncGroupId: selectedSync || null,
        playlistId: selectedPlaylist || null,
      });
      setName("");
      setLocation("");
      setSelectedPlaylist("");
      setEditingId(null);
      setShowModal(false);
      fetchDevices();
    } catch (err) {
      console.error(err);
      setError("Erro ao atualizar dispositivo.");
    }
  };

  const handleDelete = async (id: string) => {
    setError(null);
    try {
      await signageApi.delete(`/devices/${id}`);
      fetchDevices();
    } catch (err) {
      console.error(err);
      setError("Erro ao excluir dispositivo.");
    }
  };

  const handleOpenDelete = (device: Device) => {
    setDeleteTarget(device);
    setShowDeleteModal(true);
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    await handleDelete(deleteTarget.id);
    setShowDeleteModal(false);
    setDeleteTarget(null);
  };

  const handleAssign = async (deviceId: string, syncGroupId: string, playlistId?: string) => {
    setError(null);
    try {
      const target = devices.find((d) => d.id === deviceId);
      await signageApi.put(`/devices/${deviceId}`, {
        name: target?.name,
        location: target?.location,
        syncGroupId,
        playlistId: playlistId ?? target?.playlistId ?? null,
      });
      fetchDevices();
    } catch (err) {
      console.error(err);
      setError("Erro ao associar sync group.");
    }
  };

  const handlePlaylistAssign = async (deviceId: string, playlistId: string) => {
    const current = devices.find((d) => d.id === deviceId);
    await handleAssign(deviceId, current?.syncGroupId || "", playlistId);
  };

  return (
    <div className="signage-devices">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-display text-[#2A1F1B]">Digital Signage — Dispositivos</h1>
          <p className="text-sm text-[#8B7355]/70">
            Associe devices aos sync groups e selecione uma playlist override quando necessário.
          </p>
        </div>
        <Button
          onClick={handleOpenCreate}
          className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90 font-medium"
        >
          <Plus className="w-4 h-4 mr-2" />
          Adicionar device
        </Button>
      </div>

      {error && <p className="error">{error}</p>}

      <section className="table-card">
        <Table>
          <TableHeader>
            <TableRow className="border-b border-[#D7B899]/20 bg-[#D7B899]/5">
              <TableHead className="text-[#2A1F1B] font-medium">Nome</TableHead>
              <TableHead className="text-[#2A1F1B] font-medium">Localização</TableHead>
              <TableHead className="text-[#2A1F1B] font-medium">Sync Group</TableHead>
              <TableHead className="text-[#2A1F1B] font-medium">Playlist</TableHead>
              <TableHead className="text-[#2A1F1B] font-medium">Player</TableHead>
              <TableHead className="text-right text-[#2A1F1B] font-medium">Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {devices.map((device) => (
              <TableRow key={device.id} className="hover:bg-[#D7B899]/5 transition-colors">
                <TableCell className="text-[#2A1F1B] font-medium">{device.name}</TableCell>
                <TableCell>{device.location || "—"}</TableCell>
                <TableCell>
                  <select
                    value={device.syncGroupId || ""}
                    onChange={(event) => handleAssign(device.id, event.target.value, device.playlistId)}
                  >
                    <option value="">Nenhum</option>
                    {syncGroups.map((group) => (
                      <option key={group.id} value={group.id}>
                        {group.name}
                      </option>
                    ))}
                  </select>
                </TableCell>
                <TableCell>
                  <select
                    value={device.playlistId || ""}
                    onChange={(event) => handlePlaylistAssign(device.id, event.target.value)}
                  >
                    <option value="">Sem override</option>
                    {playlists.map((playlist) => (
                      <option key={playlist.id} value={playlist.id}>
                        {playlist.name}
                      </option>
                    ))}
                  </select>
                </TableCell>
                <TableCell>
                  <a
                    className="link"
                    href={`http://localhost:4020/player.html?deviceId=${device.id}`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Abrir player
                  </a>
                </TableCell>
                <TableCell className="text-right">
                  <div className="action-buttons">
                    <button type="button" className="icon-button" onClick={() => handleOpenEdit(device)} title="Editar">
                      <Pencil className="icon" />
                    </button>
                    <button type="button" className="icon-button delete" onClick={() => handleOpenDelete(device)} title="Excluir">
                      <Trash2 className="icon" />
                    </button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
            {devices.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-[#8B7355]/70">
                  Nenhum dispositivo encontrado.
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
              <h3>{editingId ? "Editar device" : "Novo device"}</h3>
              <button type="button" className="icon-button" onClick={() => setShowModal(false)}>
                <X className="icon" />
              </button>
            </div>
            <form onSubmit={editingId ? handleUpdate : handleCreate} className="modal-form">
              <label>
                Nome
                <input placeholder="Nome" value={name} onChange={(event) => setName(event.target.value)} required />
              </label>
              <label>
                Localização
                <input placeholder="Localização" value={location} onChange={(event) => setLocation(event.target.value)} />
              </label>
              <label>
                Sync Group
                <select value={selectedSync} onChange={(event) => setSelectedSync(event.target.value)}>
                  <option value="">Sem sync group</option>
                  {syncGroups.map((group) => (
                    <option key={group.id} value={group.id}>
                      {group.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Playlist (override)
                <select value={selectedPlaylist} onChange={(event) => setSelectedPlaylist(event.target.value)}>
                  <option value="">Sem override</option>
                  {playlists.map((playlist) => (
                    <option key={playlist.id} value={playlist.id}>
                      {playlist.name}
                    </option>
                  ))}
                </select>
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
              <h3>Excluir device</h3>
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
        .table-card {
          background: #fff;
          border-radius: 16px;
          box-shadow: 0 16px 32px rgba(42, 31, 27, 0.12);
          border: 1px solid rgba(215, 184, 153, 0.2);
          overflow: hidden;
          margin-bottom: 1rem;
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
        }
        input, select {
          padding: 0.55rem 0.65rem;
          border-radius: 8px;
          border: 1px solid #D7B899;
          background: #fff;
          color: #2A1F1B;
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
        .link {
          color: #6B3E26;
          font-weight: 600;
        }
        .error {
          color: #D65A31;
          margin: 0.75rem 0;
        }
      `}</style>
    </div>
  );
}
