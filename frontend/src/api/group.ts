import http from "./http";
import type { GroupDetail, MyGroup, GroupMember } from "../types/group";

export const createGroup = async (name: string) => {
    const res = await http.post<GroupDetail>("/api/groups", {
        name,
        description: null,
    });
    return res.data;
};

export const getMyGroups = async () => {
    const res = await http.get<MyGroup[]>("/api/groups/me");
    return res.data;
};

export const getGroupDetail = async (groupId: number) => {
    const res = await http.get<GroupDetail>(`/api/groups/${groupId}`);
    return res.data;
};

export const joinGroupByCode = async (inviteCode: string) => {
    const res = await http.post<GroupDetail>("/api/groups/join", { inviteCode });
    return res.data;
};

export const getGroupMembers = async (groupId: number) => {
    const res = await http.get<GroupMember[]>(`/api/groups/${groupId}/members`);
    return res.data;
};