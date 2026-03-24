export type GroupRole = "OWNER" | "ADMIN" | "MEMBER";

export interface GroupMember {
    userId: number;
    name?: string;
    email?: string;
    role: GroupRole;
}

export interface GroupDetail {
    groupId: number;
    groupName: string;
    description?: string;
    inviteCode?: string;
    memberCount?: number;
    members?: GroupMember[];
}

export interface MyGroup {
    groupId: number;
    groupName: string;
}