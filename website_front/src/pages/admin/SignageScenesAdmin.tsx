import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Plus, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import signageApi from '@/services/signageApi';

type SceneItem = {
  id: string;
  device_id: string;
  playlist_id: string;
};

type SyncGroup = {
  id: string;
  name: string;
};

type Device = {
  id: string;
  name: string;
  syncGroupId?: string;
};

type Playlist = {
  id: string;
  name: string;
};

export default function SignageScenesAdmin() {
  const [syncGroups, setSyncGroups] = useState<SyncGroup[]>([]);
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [scenes, setScenes] = useState<any[]>([]);
  const [sceneItems, setSceneItems] = useState<Record<string, SceneItem[]>>({});
  const [selectedGroup, setSelectedGroup] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newSceneName, setNewSceneName] = useState('');
  const [createError, setCreateError] = useState('');
  const [sceneError, setSceneError] = useState('');
  const [addingSceneId, setAddingSceneId] = useState('');
  const [selectedDevice, setSelectedDevice] = useState('');
  const [selectedPlaylist, setSelectedPlaylist] = useState('');

  const filteredDevices = useMemo(
    () =>
      devices.filter(
        (device) => device.syncGroupId && device.syncGroupId === selectedGroup
      ),
    [devices, selectedGroup]
  );

  useEffect(() => {
    fetchData();
  }, []);

  useEffect(() => {
    if (selectedGroup) {
      fetchScenes();
    }
  }, [selectedGroup]);

  async function fetchData() {
    try {
      const [groupsRes, playlistsRes, devicesRes] = await Promise.all([
        signageApi.get('/sync-groups'),
        signageApi.get('/playlists'),
        signageApi.get('/devices'),
      ]);

      setSyncGroups(groupsRes.data);
      setPlaylists(playlistsRes.data);
      setDevices(
        devicesRes.data.map((device: any) => ({
          id: device.id,
          name: device.name,
          syncGroupId: device.syncGroupId ?? device.sync_group_id,
        }))
      );

      if (!selectedGroup && groupsRes.data.length) {
        setSelectedGroup(groupsRes.data[0].id);
      }
    } catch (err) {
      setCreateError('Erro ao carregar dados iniciais.');
    }
  }

  async function fetchScenes() {
    try {
      const response = await signageApi.get('/sync-scenes', {
        params: { syncGroupId: selectedGroup },
      });
      setScenes(response.data);
      response.data.forEach((scene: any) => fetchSceneItems(scene.id));
    } catch (err) {
      setSceneError('Erro ao buscar scenes.');
    }
  }

  async function fetchSceneItems(sceneId: string) {
    try {
      const response = await signageApi.get(`/sync-scenes/${sceneId}/items`);
      setSceneItems((prev) => ({
        ...prev,
        [sceneId]: response.data,
      }));
    } catch (err) {
      setSceneError('Erro ao carregar itens da cena.');
    }
  }

  const handleCreateScene = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setCreateError('');
    if (!newSceneName.trim() || !selectedGroup) {
      setCreateError('Nome e grupo são obrigatórios.');
      return;
    }

    try {
      await signageApi.post('/sync-scenes', {
        name: newSceneName.trim(),
        syncGroupId: selectedGroup,
      });
      setShowCreateModal(false);
      setNewSceneName('');
      fetchScenes();
    } catch (err: any) {
      setCreateError(err?.response?.data?.error || 'Erro ao criar cena.');
    }
  };

  const handleRemoveItem = async (sceneId: string, itemId: string) => {
    try {
      await signageApi.delete(`/sync-scenes/${sceneId}/items/${itemId}`);
      fetchSceneItems(sceneId);
    } catch (err: any) {
      setSceneError(err?.response?.data?.error || 'Erro ao remover item.');
    }
  };

  const handleAddItem = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!addingSceneId || !selectedDevice || !selectedPlaylist) {
      setSceneError('Dispositivo e playlist são obrigatórios.');
      return;
    }

    try {
      await signageApi.post(`/sync-scenes/${addingSceneId}/items`, {
        deviceId: selectedDevice,
        playlistId: selectedPlaylist,
      });
      setSelectedDevice('');
      setSelectedPlaylist('');
      fetchSceneItems(addingSceneId);
    } catch (err: any) {
      setSceneError(err?.response?.data?.error || 'Erro ao adicionar item.');
    }
  };

  return (
    <div className="scenes-page">
      <div className="page-head">
        <div>
          <h1 className="text-2xl font-display text-[#2A1F1B]">
            Digital Signage — Cenas sincronizadas
          </h1>
          <p className="text-sm text-[#8B7355]/70">
            Crie cenas, vincule playlists por device e mantenha o grupo sincronizado.
          </p>
        </div>
        <Button
          onClick={() => setShowCreateModal(true)}
          className="bg-[#D7B899] text-[#2A1F1B] hover:bg-[#D7B899]/90 font-medium"
        >
          <Plus className="w-4 h-4 mr-2" />
          Nova cena
        </Button>
      </div>

      {createError && <p className="error">{createError}</p>}
      {sceneError && <p className="error">{sceneError}</p>}

      <div className="filters">
        <label>
          Sync Group
          <select value={selectedGroup} onChange={(event) => setSelectedGroup(event.target.value)}>
            {syncGroups.map((group) => (
              <option key={group.id} value={group.id}>
                {group.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="scene-list">
        {scenes.map((scene: any) => {
          const items = sceneItems[scene.id] || [];
          return (
            <div key={scene.id} className="scene-card">
              <header>
                <div>
                  <span className="scene-name">{scene.name}</span>
                  <small>
                    Group:{' '}
                    {syncGroups.find((g) => g.id === scene.sync_group_id)?.name || '—'}
                  </small>
                </div>
                <div>
                  <button
                    type="button"
                    className="primary"
                    onClick={() => {
                      setAddingSceneId(scene.id);
                      setSceneError('');
                    }}
                  >
                    <Plus className="w-4 h-4" />
                    Adicionar item
                  </button>
                </div>
              </header>
              <div className="items">
                {items.length > 0 ? (
                  items.map((item: SceneItem) => (
                  <div key={item.id} className="item-row">
                    <span>
                      {devices.find((d) => d.id === item.device_id)?.name || item.device_id}
                    </span>
                    <span>
                      {playlists.find((p) => p.id === item.playlist_id)?.name ||
                        item.playlist_id}
                    </span>
                    <button
                      type="button"
                      className="remove-item"
                      onClick={() => handleRemoveItem(scene.id, item.id)}
                    >
                      Remover
                    </button>
                  </div>
                  ))
                ) : (
                  <p className="empty">Nenhum dispositivo vinculado ainda.</p>
                )}
              </div>
            </div>
          );
        })}
        {scenes.length === 0 && <p className="empty">Nenhuma cena criada.</p>}
      </div>

      {showCreateModal && (
        <div className="modal-backdrop" onClick={() => setShowCreateModal(false)}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Nova cena sincronizada</h3>
              <button type="button" className="icon-button" onClick={() => setShowCreateModal(false)}>
                <X className="icon" />
              </button>
            </div>
            <form onSubmit={handleCreateScene} className="modal-form">
              <label>
                Nome
                <input
                  value={newSceneName}
                  onChange={(event) => setNewSceneName(event.target.value)}
                  required
                />
              </label>
              <label>
                Sync Group
                <select value={selectedGroup} onChange={(event) => setSelectedGroup(event.target.value)}>
                  {syncGroups.map((group) => (
                    <option key={group.id} value={group.id}>
                      {group.name}
                    </option>
                  ))}
                </select>
              </label>
              <div className="modal-actions">
                <Button variant="outline" className="secondary" onClick={() => setShowCreateModal(false)}>
                  Cancelar
                </Button>
                <Button type="submit" className="primary">
                  Criar
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {addingSceneId && (
        <div className="modal-backdrop" onClick={() => setAddingSceneId('')}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Adicionar item à cena</h3>
              <button
                type="button"
                className="icon-button"
                onClick={() => setAddingSceneId('')}
              >
                <X className="icon" />
              </button>
            </div>
            <form onSubmit={handleAddItem} className="modal-form">
              <label>
                Dispositivo
                <select
                  value={selectedDevice}
                  onChange={(event) => setSelectedDevice(event.target.value)}
                  required
                >
                  <option value="">Selecione um device</option>
                  {filteredDevices.map((device) => (
                    <option key={device.id} value={device.id}>
                      {device.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Playlist
                <select
                  value={selectedPlaylist}
                  onChange={(event) => setSelectedPlaylist(event.target.value)}
                  required
                >
                  <option value="">Selecione uma playlist</option>
                  {playlists.map((playlist) => (
                    <option key={playlist.id} value={playlist.id}>
                      {playlist.name}
                    </option>
                  ))}
                </select>
              </label>
              <div className="modal-actions">
                <Button variant="outline" className="secondary" onClick={() => setAddingSceneId('')}>
                  Cancelar
                </Button>
                <Button type="submit" className="primary">
                  Adicionar
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      <style>{`
        .scenes-page {
          display: flex;
          flex-direction: column;
          gap: 1rem;
        }
        .page-head {
          display: flex;
          justify-content: space-between;
          align-items: center;
          flex-wrap: wrap;
          gap: 1rem;
        }
        .filters {
          display: flex;
          gap: 1rem;
          align-items: center;
        }
        label {
          display: flex;
          flex-direction: column;
          font-weight: 600;
          color: #2a1f1b;
        }
        select {
          margin-top: 0.35rem;
          padding: 0.45rem 0.5rem;
          border-radius: 8px;
          border: 1px solid #d7b899;
        }
        .scene-list {
          display: grid;
          gap: 1rem;
        }
        .scene-card {
          background: #fff;
          border-radius: 14px;
          border: 1px solid #e2e8f0;
          padding: 1rem;
          box-shadow: 0 6px 18px rgba(42, 31, 27, 0.08);
        }
        .scene-card header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
        }
        .scene-name {
          font-weight: 600;
          font-size: 1rem;
        }
        .items {
          margin-top: 1rem;
          display: grid;
          gap: 0.5rem;
        }
        .item-row {
          display: flex;
          justify-content: space-between;
          padding: 0.5rem;
          border-radius: 8px;
          background: #f8fafc;
          font-size: 0.9rem;
          color: #2a1f1b;
          gap: 1rem;
        }
        .remove-item {
          background: transparent;
          border: none;
          color: #d65a31;
          font-weight: 600;
          cursor: pointer;
        }
        .empty {
          color: #94a3b8;
          font-style: italic;
        }
        .error {
          color: #d65a31;
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
          justify-content: space-between;
          align-items: center;
          margin-bottom: 1rem;
        }
        .modal-form {
          display: grid;
          gap: 0.75rem;
        }
        .modal-actions {
          display: flex;
          justify-content: flex-end;
          gap: 0.5rem;
        }
        .icon-button {
          background: transparent;
          border: none;
          padding: 0.25rem;
          border-radius: 6px;
          cursor: pointer;
        }
        .icon {
          width: 18px;
          height: 18px;
        }
        .primary {
          background: #d7b899;
          color: #2a1f1b;
          border: none;
          border-radius: 8px;
          padding: 0.6rem 1.25rem;
          font-weight: 600;
          cursor: pointer;
          display: inline-flex;
          align-items: center;
        }
        .primary .w-4 {
          margin-right: 0.5rem;
        }
        .secondary {
          padding: 0.6rem 1.2rem;
          border: 1px solid #d7b899;
          border-radius: 6px;
          background: #f5ede6;
          color: #2a1f1b;
          font-weight: 600;
          cursor: pointer;
        }
      `}</style>
    </div>
  );
}
