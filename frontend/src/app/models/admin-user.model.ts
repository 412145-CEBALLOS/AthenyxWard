export type UserRole = 'ADMIN' | 'PREMIUM' | 'TRIAL';

export interface AdminUser {
  id: number;
  name: string;
  email: string;
  pictureUrl: string | null;
  role: UserRole;
  trialEndDate: string | null;
  analysisCount: number;
  lastLogin: string | null;
  isActive: boolean;
  createdAt: string;
}

export interface AdminUserDetail {
  id: number;
  name: string;
  email: string;
  pictureUrl: string | null;
  googleId: string;
  role: UserRole;
  trialEndDate: string | null;
  analysisCount: number;
  lastLogin: string | null;
  createdAt: string;
  isActive: boolean;
  deletedAt: string | null;
  emailCount: number;
  reminderCount: number;
}

export interface AdminUserListResponse {
  items: AdminUser[];
  currentPage: number;
  totalPages: number;
  totalItems: number;
}

export interface UpdateRoleRequest {
  role: UserRole;
}

export interface UpdateActiveRequest {
  active: boolean;
}

export interface ResetTrialResponse {
  trialEndDate: string;
  analysisCount: number;
}

export interface UserSearchResult {
  id: number;
  name: string;
  email: string;
  pictureUrl: string | null;
  role: UserRole;
  isActive: boolean;
}

export interface UserFilters {
  query?: string;
  role?: UserRole | '';
  active?: boolean | '';
  page?: number;
  size?: number;
}
