import { useEffect, useMemo, useState, FormEvent } from "react";
import { PlusCircle, X, Trash2, Pencil } from "lucide-react";
import signageApi from "@/services/signageApi";

type Playlist = {
  id: string;
  name: string;
  mode: "slots" | "durations";
};

type PlaylistItem = {
  id: string;
  order: number;
  durationSeconds?: number;
  screenId: string;
};

type Schedule = {
  id: string;
  name: string;
  timezone: string;
};

type ScheduleWindow = {
  id: string;
  playlistId?: string;
  syncSceneId?: string;
  startTime: string;
  endTime: string;
  priority: number;
};

type Notice = {
  id: string;
  title: string;
  type?: string;
};

export default function SignageScheduleAdmin() {
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [scheduleList, setScheduleList] = useState<Schedule[]>([]);
  const [selectedPlaylist, setSelectedPlaylist] = useState<string>("");
  const [playlistItemsById, setPlaylistItemsById] = useState<Record<string, PlaylistItem[]>>({});
const [windows, setWindows] = useState<Record<string, ScheduleWindow[]>>({});
const [syncGroups, setSyncGroups] = useState<any[]>([]);
const [scheduleSyncGroups, setScheduleSyncGroups] = useState<Record<string, string[]>>({});
const [scenesByGroup, setScenesByGroup] = useState<Record<string, any[]>>({});
  const [playlistName, setPlaylistName] = useState("");
  const [playlistMode, setPlaylistMode] = useState<"durations" | "slots">("durations");
  const [noticeList, setNoticeList] = useState<Notice[]>([]);
  const [selectedNotice, setSelectedNotice] = useState("");
  const [showAddNoticeModal, setShowAddNoticeModal] = useState(false);
  const [addNoticePlaylistId, setAddNoticePlaylistId] = useState<string>("");
  const [scheduleName, setScheduleName] = useState("");
  const scheduleTimezone = (import.meta.env.VITE_SIGNAGE_TIMEZONE as string | undefined) || "America/Sao_Paulo";
  const [windowStart, setWindowStart] = useState("00:00");
  const [windowEnd, setWindowEnd] = useState("23:59");
  const [windowPlaylist, setWindowPlaylist] = useState("");
  const [windowPriority, setWindowPriority] = useState(0);
  const [selectedScheduleId, setSelectedScheduleId] = useState<string>("");
  const [error, setError] = useState<string | null>(null);
  const [showEditPlaylistModal, setShowEditPlaylistModal] = useState(false);
  const [editingPlaylistId, setEditingPlaylistId] = useState("");
  const [editingPlaylistName, setEditingPlaylistName] = useState("");
  const [showAddPlaylistModal, setShowAddPlaylistModal] = useState(false);
  const [showAddScheduleModal, setShowAddScheduleModal] = useState(false);
  const [showEditScheduleModal, setShowEditScheduleModal] = useState(false);
  const [editingScheduleId, setEditingScheduleId] = useState("");
  const [editingScheduleName, setEditingScheduleName] = useState("");
  const [showAddWindowModal, setShowAddWindowModal] = useState(false);
const [addWindowScheduleId, setAddWindowScheduleId] = useState("");
const [windowMode, setWindowMode] = useState<"playlist" | "scene">("playlist");
const [windowScene, setWindowScene] = useState("");

  const fetchNotices = async () => {
    try {
      const response = await signageApi.get("/screens");
      setNoticeList(response.data);
    } catch (err) {
      console.error(err);
      setError("Erro ao carregar mídias.");
    }
  };

  const fetchPlaylists = async () => {
    try {
      const response = await signageApi.get("/playlists");
      setPlaylists(response.data);
      if (!selectedPlaylist && response.data.length > 0) {
        setSelectedPlaylist(response.data[0].id);
      }
      if (response.data.length > 0) {
        await Promise.all(response.data.map((playlist: Playlist) => fetchPlaylistItems(playlist.id)));
      }
    } catch (err) {
      console.error(err);
      setError("Não foi possível carregar playlists.");
    }
  };

  const fetchPlaylistItems = async (playlistId: string) => {
    if (!playlistId) return;
    try {
      const response = await signageApi.get(`/playlists/${playlistId}/items`);
      const normalized = response.data.map((item: any) => ({
        id: item.id,
        order: item.order,
        durationSeconds: item.durationSeconds ?? item.duration_seconds,
        screenId: item.screenId ?? item.screen_id,
      }));
      setPlaylistItemsById((prev) => ({
        ...prev,
        [playlistId]: normalized,
      }));
    } catch (err) {
      console.error(err);
      setError("Erro ao buscar itens da playlist.");
    }
  };

  const fetchSchedules = async () => {
    try {
      const response = await signageApi.get("/schedules");
      setScheduleList(response.data);
      if (!selectedScheduleId && response.data.length > 0) {
        setSelectedScheduleId(response.data[0].id);
      }
      await Promise.all(response.data.map((schedule: Schedule) => fetchScheduleWindows(schedule.id)));
    } catch (err) {
      console.error(err);
      setError("Não foi possível carregar agendas.");
    }
  };

  const fetchScheduleWindows = async (scheduleId: string) => {
    if (!scheduleId) return;
    try {
      const response = await signageApi.get(`/schedules/${scheduleId}/windows`);
      const normalized = response.data.map((item: any) => ({
        id: item.id,
        playlistId: item.playlistId ?? item.playlist_id,
        syncSceneId: item.syncSceneId ?? item.sync_scene_id ?? null,
        startTime: item.startTime ?? item.start_time,
        endTime: item.endTime ?? item.end_time,
        priority: item.priority ?? 0,
      }));
      setWindows((prev) => ({
        ...prev,
        [scheduleId]: normalized,
      }));
    } catch (err) {
      console.error(err);
      setError("Erro ao carregar janelas.");
    }
  };

  useEffect(() => {
    fetchPlaylists();
    fetchSchedules();
    fetchNotices();
    fetchSyncGroups();
  }, []);

  useEffect(() => {
    if (selectedPlaylist) {
      if (!playlistItemsById[selectedPlaylist]) {
        fetchPlaylistItems(selectedPlaylist);
      }
    }
  }, [selectedPlaylist, playlistItemsById]);

  useEffect(() => {
  if (selectedScheduleId) {
    fetchScheduleWindows(selectedScheduleId);
  }
}, [selectedScheduleId]);

const sceneNameById = useMemo(() => {
  return Object.values(scenesByGroup)
    .flat()
    .reduce((acc: Record<string, string>, scene: any) => {
      acc[scene.id] = scene.name;
      return acc;
    }, {});
}, [scenesByGroup]);

  const fetchSyncGroups = async () => {
    try {
      const response = await signageApi.get("/sync-groups");
      setSyncGroups(response.data);
      const mapping: Record<string, string[]> = {};
      response.data.forEach((group: any) => {
        const scheduleKey = group.scheduleId ?? group.schedule_id;
        if (!scheduleKey) return;
        mapping[scheduleKey] = mapping[scheduleKey] || [];
        mapping[scheduleKey].push(group.id);
        fetchScenesForGroup(group.id);
      });
      setScheduleSyncGroups(mapping);
    } catch (err) {
      console.error(err);
      setError("Erro ao carregar sync groups.");
    }
  };

  const fetchScenesForGroup = async (groupId: string) => {
    try {
      const response = await signageApi.get("/sync-scenes", {
        params: { syncGroupId: groupId },
      });
      setScenesByGroup((prev) => ({
        ...prev,
        [groupId]: response.data,
      }));
    } catch {
      // ignore
    }
  };

  const handleCreatePlaylist = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    try {
      await signageApi.post("/playlists", { name: playlistName, mode: playlistMode });
      setPlaylistName("");
      setPlaylistMode("durations");
      setShowAddPlaylistModal(false);
      fetchPlaylists();
    } catch (err) {
      console.error(err);
      setError("Erro ao criar playlist.");
    }
  };

  const handleOpenEditPlaylist = (playlist: Playlist) => {
    setEditingPlaylistId(playlist.id);
    setEditingPlaylistName(playlist.name);
    setShowEditPlaylistModal(true);
  };

  const handleUpdatePlaylist = async (event: FormEvent) => {
    event.preventDefault();
    if (!editingPlaylistId) return;
    setError(null);
    try {
      await signageApi.put(`/playlists/${editingPlaylistId}`, { name: editingPlaylistName });
      setShowEditPlaylistModal(false);
      setEditingPlaylistId("");
      setEditingPlaylistName("");
      fetchPlaylists();
    } catch (err) {
      console.error(err);
      setError("Erro ao atualizar playlist.");
    }
  };

  const handleDeletePlaylist = async (playlistId: string) => {
    setError(null);
    try {
      await signageApi.delete(`/playlists/${playlistId}`);
      if (selectedPlaylist === playlistId) {
        setSelectedPlaylist("");
      }
      fetchPlaylists();
    } catch (err) {
      console.error(err);
      setError("Erro ao excluir playlist.");
    }
  };

  const handleAddItem = async (event: FormEvent) => {
    event.preventDefault();
    const targetPlaylist = addNoticePlaylistId || selectedPlaylist;
    if (!targetPlaylist || !selectedNotice) return;
    setError(null);
    try {
      const currentItems = playlistItemsById[targetPlaylist] || [];
      const order = currentItems.length + 1;
      await signageApi.post(`/playlists/${targetPlaylist}/items`, {
        screenId: selectedNotice,
        order,
        durationSeconds: 5,
      });
      setSelectedNotice("");
      fetchPlaylistItems(targetPlaylist);
      setShowAddNoticeModal(false);
    } catch (err) {
      console.error(err);
      setError("Erro ao adicionar item.");
    }
  };

  const handleRemoveItem = async (playlistId: string, itemId: string) => {
    setError(null);
    try {
      await signageApi.delete(`/playlists/${playlistId}/items/${itemId}`);
      fetchPlaylistItems(playlistId);
    } catch (err) {
      console.error(err);
      setError("Erro ao remover item.");
    }
  };

  const handleCreateWindow = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    const usesScene = windowMode === "scene";
    if (!windowStart || !windowEnd) {
      return;
    }
    if (usesScene && !windowScene) {
      setError("Selecione uma cena sincronizada.");
      return;
    }
    if (!usesScene && !windowPlaylist) {
      setError("Selecione uma playlist.");
      return;
    }

    const targetSchedule = addWindowScheduleId || selectedScheduleId || scheduleList[0]?.id;
    if (!targetSchedule) return;

    try {
      await signageApi.post(`/schedules/${targetSchedule}/windows`, {
        startTime: windowStart,
        endTime: windowEnd,
        playlistId: usesScene ? null : windowPlaylist,
        syncSceneId: usesScene ? windowScene : null,
        priority: windowPriority,
      });
      setShowAddWindowModal(false);
      setWindowMode("playlist");
      setWindowScene("");
      setWindowPlaylist("");
      fetchScheduleWindows(targetSchedule);
    } catch (err) {
      console.error(err);
      setError("Erro ao criar janela.");
    }
  };

  const handleOpenAddWindow = (scheduleId: string) => {
    setAddWindowScheduleId(scheduleId);
    setWindowMode("playlist");
    setWindowScene("");
    setWindowPlaylist("");
    setWindowStart("00:00");
    setWindowEnd("23:59");
    setWindowPriority(0);
    setShowAddWindowModal(true);
  };

  const availableScenesForSchedule = useMemo(() => {
    const groupIds = scheduleSyncGroups[addWindowScheduleId || selectedScheduleId || scheduleList[0]?.id] || [];
    return groupIds.flatMap((groupId) => scenesByGroup[groupId] || []);
  }, [addWindowScheduleId, selectedScheduleId, scheduleList, scheduleSyncGroups, scenesByGroup]);

  const handleCreateSchedule = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    try {
      await signageApi.post("/schedules", { name: scheduleName, timezone: scheduleTimezone });
      setScheduleName("");
      setShowAddScheduleModal(false);
      fetchSchedules();
    } catch (err) {
      console.error(err);
      setError("Erro ao criar schedule.");
    }
  };

  const handleOpenEditSchedule = (schedule: Schedule) => {
    setEditingScheduleId(schedule.id);
    setEditingScheduleName(schedule.name);
    setShowEditScheduleModal(true);
  };

  const handleUpdateSchedule = async (event: FormEvent) => {
    event.preventDefault();
    if (!editingScheduleId) return;
    setError(null);
    try {
      await signageApi.put(`/schedules/${editingScheduleId}`, {
        name: editingScheduleName,
        timezone: scheduleTimezone,
      });
      setShowEditScheduleModal(false);
      setEditingScheduleId("");
      setEditingScheduleName("");
      fetchSchedules();
    } catch (err) {
      console.error(err);
      setError("Erro ao atualizar agenda.");
    }
  };

  const handleDeleteSchedule = async (scheduleId: string) => {
    setError(null);
    try {
      await signageApi.delete(`/schedules/${scheduleId}`);
      if (selectedScheduleId === scheduleId) {
        setSelectedScheduleId("");
      }
      fetchSchedules();
    } catch (err) {
      console.error(err);
      setError("Erro ao excluir agenda.");
    }
  };

  const handleRemoveWindow = async (scheduleId: string, windowId: string) => {
    setError(null);
    try {
      await signageApi.delete(`/schedules/${scheduleId}/windows/${windowId}`);
      fetchScheduleWindows(scheduleId);
    } catch (err) {
      console.error(err);
      setError("Erro ao remover janela.");
    }
  };

  const getNoticeTitle = (screenId: string) =>
    noticeList.find((notice) => notice.id === screenId)?.title || screenId;

  const getPlaylistName = (playlistId: string) =>
    playlists.find((playlist) => playlist.id === playlistId)?.name || playlistId;

  return (
    <div className="page">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-display text-[#2A1F1B]">Digital Signage — Agenda e Playlists</h1>
          <p className="text-sm text-[#8B7355]/70">Organize playlists e programe janelas por agenda.</p>
        </div>
      </div>

      {error && <p className="error">{error}</p>}

      <div className="grid">
        <section className="card">
          <div className="section-head">
            <h2>Playlists</h2>
            <button type="button" className="primary" onClick={() => setShowAddPlaylistModal(true)}>
              Criar playlist
            </button>
          </div>

          <div className="list">
            {playlists.map((playlist) => {
              const items = playlistItemsById[playlist.id] || [];
              const isActive = selectedPlaylist === playlist.id;
              return (
                <div key={playlist.id} className={`accordion-item ${isActive ? "active" : ""}`}>
                  <div className={`list-item ${isActive ? "active" : ""}`}>
                    <button
                      type="button"
                      className="list-item-main"
                      onClick={() => setSelectedPlaylist(playlist.id)}
                    >
                      <span>{playlist.name}</span>
                      <em>• {items.length} itens</em>
                    </button>
                    <div className="list-actions">
                      <button
                        type="button"
                        className="icon-button"
                        onClick={() => handleOpenEditPlaylist(playlist)}
                        title="Editar playlist"
                      >
                        <Pencil className="icon" />
                      </button>
                      <button
                        type="button"
                        className="icon-button delete"
                        onClick={() => handleDeletePlaylist(playlist.id)}
                        title="Excluir playlist"
                      >
                        <Trash2 className="icon" />
                      </button>
                      <button
                        type="button"
                        className="add-icon"
                        onClick={() => {
                          setSelectedPlaylist(playlist.id);
                          setAddNoticePlaylistId(playlist.id);
                          setSelectedNotice("");
                          setShowAddNoticeModal(true);
                        }}
                        title="Adicionar mídia"
                      >
                        <PlusCircle className="icon" />
                      </button>
                    </div>
                  </div>
                  {isActive && (
                    <div className="accordion-body">
                      {items.length > 0 ? (
                        <ul className="item-list">
                          {items.map((item) => (
                            <li key={item.id}>
                              <span>
                                {getNoticeTitle(item.screenId)} <span>(ordem {item.order})</span>
                              </span>
                              <button
                                type="button"
                                className="remove-item"
                                onClick={() => handleRemoveItem(playlist.id, item.id)}
                                title="Remover mídia"
                              >
                                <Trash2 className="icon" />
                              </button>
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <div className="empty">Nenhuma mídia vinculada.</div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
            {playlists.length === 0 && <div className="empty">Nenhuma playlist criada.</div>}
          </div>
        </section>

        <section className="card">
          <div className="section-head">
            <h2>Schedules</h2>
            <button type="button" className="primary" onClick={() => setShowAddScheduleModal(true)}>
              Criar agenda
            </button>
          </div>

          <div className="list">
            {scheduleList.map((schedule) => {
              const isActive = selectedScheduleId === schedule.id;
              const scheduleWindows = windows[schedule.id] || [];
              return (
                <div key={schedule.id} className={`accordion-item ${isActive ? "active" : ""}`}>
                  <div className={`list-item ${isActive ? "active" : ""}`}>
                    <button
                      type="button"
                      className="list-item-main"
                      onClick={() => setSelectedScheduleId(schedule.id)}
                    >
                      <span>{schedule.name}</span>
                      <em>• {scheduleWindows.length} janelas</em>
                    </button>
                    <div className="list-actions">
                      <button
                        type="button"
                        className="icon-button"
                        onClick={() => handleOpenEditSchedule(schedule)}
                        title="Editar agenda"
                      >
                        <Pencil className="icon" />
                      </button>
                      <button
                        type="button"
                        className="icon-button delete"
                        onClick={() => handleDeleteSchedule(schedule.id)}
                        title="Excluir agenda"
                      >
                        <Trash2 className="icon" />
                      </button>
                      <button
                        type="button"
                        className="add-icon"
                        onClick={() => {
                          setSelectedScheduleId(schedule.id);
                          handleOpenAddWindow(schedule.id);
                        }}
                        title="Adicionar janela"
                      >
                        <PlusCircle className="icon" />
                      </button>
                    </div>
                  </div>
                  {isActive && (
                    <div className="accordion-body">
                      {scheduleWindows.length > 0 ? (
                        <ul className="item-list">
                          {scheduleWindows.map((window) => {
                            const contentLabel = window.syncSceneId
                              ? `Cena: ${sceneNameById[window.syncSceneId] || window.syncSceneId}`
                              : `Playlist: ${getPlaylistName(window.playlistId || "")}`;
                            return (
                              <li key={window.id}>
                                <span>
                                  {window.startTime} {'->'} {window.endTime} • {contentLabel}
                                </span>
                                <button
                                  type="button"
                                  className="remove-item"
                                  onClick={() => handleRemoveWindow(schedule.id, window.id)}
                                  title="Remover janela"
                                >
                                  <Trash2 className="icon" />
                                </button>
                              </li>
                            );
                          })}
                        </ul>
                      ) : (
                        <div className="empty">Nenhuma janela criada.</div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
            {scheduleList.length === 0 && <div className="empty">Nenhuma agenda criada.</div>}
          </div>
        </section>
      </div>

      {showAddNoticeModal && (
        <div className="modal-backdrop" onClick={() => setShowAddNoticeModal(false)}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Adicionar mídia</h3>
              <button type="button" className="icon-button" onClick={() => setShowAddNoticeModal(false)}>
                <X className="icon" />
              </button>
            </div>
            <form onSubmit={handleAddItem} className="modal-form">
              <label>
                Mídia
                <select value={selectedNotice} onChange={(event) => setSelectedNotice(event.target.value)} required>
                  <option value="">Selecione mídia</option>
                  {noticeList.map((notice) => (
                    <option value={notice.id} key={notice.id}>
                      {notice.title}
                    </option>
                  ))}
                </select>
              </label>
              <div className="modal-actions">
                <button type="button" className="secondary" onClick={() => setShowAddNoticeModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="primary">Adicionar</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showEditPlaylistModal && (
        <div className="modal-backdrop" onClick={() => setShowEditPlaylistModal(false)}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Editar playlist</h3>
              <button type="button" className="icon-button" onClick={() => setShowEditPlaylistModal(false)}>
                <X className="icon" />
              </button>
            </div>
            <form onSubmit={handleUpdatePlaylist} className="modal-form">
              <label>
                Nome
                <input
                  value={editingPlaylistName}
                  onChange={(event) => setEditingPlaylistName(event.target.value)}
                  required
                />
              </label>
              <div className="modal-actions">
                <button type="button" className="secondary" onClick={() => setShowEditPlaylistModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="primary">Salvar</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showAddPlaylistModal && (
        <div className="modal-backdrop" onClick={() => setShowAddPlaylistModal(false)}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Nova playlist</h3>
              <button type="button" className="icon-button" onClick={() => setShowAddPlaylistModal(false)}>
                <X className="icon" />
              </button>
            </div>
            <form onSubmit={handleCreatePlaylist} className="modal-form">
              <label>
                Nome
                <input
                  placeholder="Nome da playlist"
                  value={playlistName}
                  onChange={(event) => setPlaylistName(event.target.value)}
                  required
                />
              </label>
              <label>
                Modo
                <select value={playlistMode} onChange={(event) => setPlaylistMode(event.target.value as any)}>
                  <option value="durations">Durations</option>
                  <option value="slots">Slots</option>
                </select>
              </label>
              <div className="modal-actions">
                <button type="button" className="secondary" onClick={() => setShowAddPlaylistModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="primary">Criar</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showAddScheduleModal && (
        <div className="modal-backdrop" onClick={() => setShowAddScheduleModal(false)}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Nova agenda</h3>
              <button type="button" className="icon-button" onClick={() => setShowAddScheduleModal(false)}>
                <X className="icon" />
              </button>
            </div>
            <form onSubmit={handleCreateSchedule} className="modal-form">
              <label>
                Nome
                <input
                  value={scheduleName}
                  onChange={(event) => setScheduleName(event.target.value)}
                  required
                />
              </label>
              <div className="modal-actions">
                <button type="button" className="secondary" onClick={() => setShowAddScheduleModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="primary">Criar</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showEditScheduleModal && (
        <div className="modal-backdrop" onClick={() => setShowEditScheduleModal(false)}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Editar agenda</h3>
              <button type="button" className="icon-button" onClick={() => setShowEditScheduleModal(false)}>
                <X className="icon" />
              </button>
            </div>
            <form onSubmit={handleUpdateSchedule} className="modal-form">
              <label>
                Nome
                <input
                  value={editingScheduleName}
                  onChange={(event) => setEditingScheduleName(event.target.value)}
                  required
                />
              </label>
              <div className="modal-actions">
                <button type="button" className="secondary" onClick={() => setShowEditScheduleModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="primary">Salvar</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showAddWindowModal && (
        <div className="modal-backdrop" onClick={() => setShowAddWindowModal(false)}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Nova janela</h3>
              <button type="button" className="icon-button" onClick={() => setShowAddWindowModal(false)}>
                <X className="icon" />
              </button>
            </div>
            <form onSubmit={handleCreateWindow} className="modal-form">
              <label>
                Início
                <input value={windowStart} onChange={(event) => setWindowStart(event.target.value)} type="time" required />
              </label>
              <label>
                Fim
                <input value={windowEnd} onChange={(event) => setWindowEnd(event.target.value)} type="time" required />
              </label>
              <div className="mode-toggle">
                <label>
                  <input
                    type="radio"
                    name="windowMode"
                    value="playlist"
                    checked={windowMode === "playlist"}
                    onChange={() => setWindowMode("playlist")}
                  />
                  Playlist
                </label>
                <label>
                  <input
                    type="radio"
                    name="windowMode"
                    value="scene"
                    checked={windowMode === "scene"}
                    onChange={() => {
                      setWindowMode("scene");
                      setWindowPlaylist("");
                    }}
                  />
                  Cena sincronizada
                </label>
              </div>
              {windowMode === "playlist" && (
                <label>
                  Playlist
                  <select value={windowPlaylist} onChange={(event) => setWindowPlaylist(event.target.value)}>
                    <option value="">Selecione playlist</option>
                    {playlists.map((playlist) => (
                      <option key={playlist.id} value={playlist.id}>
                        {playlist.name}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {windowMode === "scene" && (
                <label>
                  Cena sincronizada
                  <select value={windowScene} onChange={(event) => setWindowScene(event.target.value)}>
                    <option value="">Selecione cena</option>
                    {availableScenesForSchedule.map((scene) => (
                      <option key={scene.id} value={scene.id}>
                        {scene.name}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              <label>
                Prioridade
                <input
                  type="number"
                  value={windowPriority}
                  onChange={(event) => setWindowPriority(Number(event.target.value))}
                />
              </label>
              <div className="modal-actions">
                <button type="button" className="secondary" onClick={() => setShowAddWindowModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="primary">Criar</button>
              </div>
            </form>
          </div>
        </div>
      )}

      <style>{`
        .grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
          gap: 1.5rem;
        }
        .card {
          background: #fff;
          border-radius: 14px;
          padding: 1.25rem;
          box-shadow: 0 6px 18px rgba(42, 31, 27, 0.08);
          border: 1px solid #EADCCB;
        }
        .section-head {
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 1rem;
          margin-bottom: 0.75rem;
        }
        .section-actions {
          display: flex;
          justify-content: flex-end;
          margin: 0.5rem 0 1rem;
        }
        .form-row {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
          gap: 0.6rem;
          margin: 0.75rem 0 1rem;
          align-items: end;
        }
        .form-row.compact {
          grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
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
        .list {
          display: grid;
          gap: 0.5rem;
        }
        .accordion-item {
          border-radius: 12px;
          background: #fff;
          border: 1px solid #e2e8f0;
          overflow: hidden;
        }
        .accordion-item.active {
          border-color: #D7B899;
          box-shadow: 0 4px 12px rgba(42, 31, 27, 0.08);
        }
        .list-item {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 0.6rem 0.75rem;
          border: none;
          background: #f8fafc;
          gap: 0.5rem;
        }
        .list-item-main {
          display: flex;
          align-items: center;
          gap: 0.35rem;
          background: transparent;
          border: none;
          padding: 0;
          color: inherit;
          font: inherit;
          cursor: pointer;
        }
        .list-item.active {
          background: #F5EDE6;
          color: #2A1F1B;
          font-weight: 600;
        }
        .list-item em {
          font-style: normal;
          color: #64748b;
          font-size: 0.85rem;
        }
        .add-icon {
          background: transparent;
          border: none;
          padding: 0.15rem;
          border-radius: 8px;
          color: #8B7355;
          cursor: pointer;
        }
        .add-icon:hover {
          color: #6B3E26;
          background: rgba(215, 184, 153, 0.25);
        }
        .list-actions {
          display: flex;
          align-items: center;
          gap: 0.25rem;
        }
        .icon-button {
          background: transparent;
          border: none;
          padding: 0.15rem;
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
        .accordion-body {
          padding: 0.75rem;
          border-top: 1px solid #EADCCB;
          background: #FFF7F0;
          display: grid;
          gap: 0.75rem;
        }
        .item-list {
          margin-top: 0.75rem;
          padding-left: 1rem;
          color: #0f172a;
          display: grid;
          gap: 0.5rem;
        }
        .item-list li {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 0.5rem;
        }
        .item-list span {
          color: #64748b;
        }
        .remove-item {
          background: transparent;
          border: none;
          padding: 0.15rem;
          border-radius: 8px;
          color: #D65A31;
          cursor: pointer;
        }
        .remove-item:hover {
          background: rgba(214, 90, 49, 0.12);
        }
        .window-list {
          margin-top: 1rem;
          border-top: 1px solid #e2e8f0;
          padding-top: 0.75rem;
        }
        .empty {
          color: #94a3b8;
          font-style: italic;
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
        .mode-toggle {
          display: flex;
          gap: 0.75rem;
          margin-bottom: 0.35rem;
        }
        .mode-toggle label {
          display: flex;
          align-items: center;
          gap: 0.35rem;
          font-weight: 600;
          color: #2A1F1B;
        }
        .modal-form select {
          margin-top: 0.35rem;
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
      `}</style>
    </div>
  );
}
