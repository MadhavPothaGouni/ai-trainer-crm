import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { useAuth } from "../../auth/useAuth";
import type { AuthContextValue } from "../../auth/AuthContext";
import * as authApi from "../../api/auth";
import * as usersApi from "../../api/users";
import { ApiError } from "../../lib/apiClient";
import type { UserDto } from "../../types/api";
import ProfilePage from "./ProfilePage";

vi.mock("../../auth/useAuth", () => ({ useAuth: vi.fn() }));
vi.mock("../../api/users", () => ({ updateMyProfile: vi.fn() }));
vi.mock("../../api/auth", () => ({ changePassword: vi.fn() }));

const CURRENT_USER: UserDto = {
  id: "u1",
  email: "owner@example.com",
  firstName: "Ada",
  lastName: "Lovelace",
  fullName: "Ada Lovelace",
  phone: "555-0100",
  avatarUrl: null,
  timezone: "UTC",
  locale: "en-US",
  status: "ACTIVE",
  emailVerified: true,
  mfaEnabled: false,
  teamId: null,
  managerId: null,
  roles: ["OWNER"],
  lastLoginAt: null,
  createdAt: "2026-01-01T00:00:00Z",
};

function mockAuth(overrides: Partial<AuthContextValue>) {
  vi.mocked(useAuth).mockReturnValue({
    user: CURRENT_USER,
    isInitializing: false,
    isAuthenticated: true,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
    ...overrides,
  });
}

function renderProfilePage() {
  return render(
    <MemoryRouter initialEntries={["/profile"]}>
      <Routes>
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/login" element={<div>Login landing</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ProfilePage", () => {
  it("pre-fills the details form from the current user", () => {
    mockAuth({});
    renderProfilePage();

    expect(screen.getByLabelText("First name")).toHaveValue("Ada");
    expect(screen.getByLabelText("Last name")).toHaveValue("Lovelace");
    expect(screen.getByLabelText("Phone")).toHaveValue("555-0100");
    expect(screen.getByLabelText("Timezone")).toHaveValue("UTC");
    expect(screen.getByLabelText("Email")).toHaveValue("owner@example.com");
    expect(screen.getByLabelText("Email")).toBeDisabled();
  });

  it("saves profile changes and refreshes the current user", async () => {
    const refreshUser = vi.fn().mockResolvedValue(undefined);
    mockAuth({ refreshUser });
    vi.mocked(usersApi.updateMyProfile).mockResolvedValue(CURRENT_USER);
    const user = userEvent.setup();
    renderProfilePage();

    await user.clear(screen.getByLabelText("Last name"));
    await user.type(screen.getByLabelText("Last name"), "King");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => {
      expect(usersApi.updateMyProfile).toHaveBeenCalledWith({
        firstName: "Ada",
        lastName: "King",
        phone: "555-0100",
        timezone: "UTC",
        locale: "en-US",
      });
    });
    expect(refreshUser).toHaveBeenCalled();
    expect(await screen.findByText("Profile updated.")).toBeInTheDocument();
  });

  it("rejects a change-password submission when confirmation doesn't match", async () => {
    mockAuth({});
    const user = userEvent.setup();
    renderProfilePage();

    await user.type(screen.getByLabelText("Current password"), "OldPassw0rd!");
    await user.type(screen.getByLabelText("New password"), "NewPassw0rd!");
    await user.type(screen.getByLabelText("Confirm new password"), "Different1!");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByText("Passwords do not match")).toBeInTheDocument();
    expect(authApi.changePassword).not.toHaveBeenCalled();
  });

  it("changes the password, signs out, and redirects to login", async () => {
    const logout = vi.fn().mockResolvedValue(undefined);
    mockAuth({ logout });
    vi.mocked(authApi.changePassword).mockResolvedValue(null);
    const user = userEvent.setup();
    renderProfilePage();

    await user.type(screen.getByLabelText("Current password"), "OldPassw0rd!");
    await user.type(screen.getByLabelText("New password"), "NewPassw0rd!");
    await user.type(screen.getByLabelText("Confirm new password"), "NewPassw0rd!");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    await waitFor(() => {
      expect(authApi.changePassword).toHaveBeenCalledWith({
        currentPassword: "OldPassw0rd!",
        newPassword: "NewPassw0rd!",
      });
    });
    expect(logout).toHaveBeenCalled();
    expect(await screen.findByText("Login landing")).toBeInTheDocument();
  });

  it("shows the server's error instead of navigating when changing the password fails", async () => {
    const logout = vi.fn();
    mockAuth({ logout });
    vi.mocked(authApi.changePassword).mockRejectedValue(new ApiError({ message: "Incorrect password" }, 400));
    const user = userEvent.setup();
    renderProfilePage();

    await user.type(screen.getByLabelText("Current password"), "WrongPassw0rd!");
    await user.type(screen.getByLabelText("New password"), "NewPassw0rd!");
    await user.type(screen.getByLabelText("Confirm new password"), "NewPassw0rd!");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Incorrect password");
    expect(logout).not.toHaveBeenCalled();
  });
});
