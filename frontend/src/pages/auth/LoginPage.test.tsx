import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { useAuth } from "../../auth/useAuth";
import type { AuthContextValue } from "../../auth/AuthContext";
import { ApiError } from "../../lib/apiClient";
import LoginPage from "./LoginPage";

vi.mock("../../auth/useAuth", () => ({ useAuth: vi.fn() }));

function mockAuth(overrides: Partial<AuthContextValue>) {
  vi.mocked(useAuth).mockReturnValue({
    user: null,
    isInitializing: false,
    isAuthenticated: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
    ...overrides,
  });
}

function renderLoginPage() {
  return render(
    <MemoryRouter initialEntries={["/login"]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<div>Dashboard landing</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("LoginPage", () => {
  it("shows validation errors instead of calling login when submitted empty", async () => {
    const login = vi.fn();
    mockAuth({ login });
    const user = userEvent.setup();
    renderLoginPage();

    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByText("Email is required")).toBeInTheDocument();
    expect(screen.getByText("Password is required")).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it("logs in with the entered credentials and navigates to the dashboard", async () => {
    const login = vi.fn().mockResolvedValue(undefined);
    mockAuth({ login });
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(screen.getByLabelText("Email"), "owner@example.com");
    await user.type(screen.getByLabelText("Password"), "Str0ng!Passw0rd");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => {
      expect(login).toHaveBeenCalledWith({ email: "owner@example.com", password: "Str0ng!Passw0rd" });
    });
    expect(await screen.findByText("Dashboard landing")).toBeInTheDocument();
  });

  it("shows the server's error message and does not navigate on failed login", async () => {
    const login = vi.fn().mockRejectedValue(new ApiError({ message: "Invalid email or password" }, 401));
    mockAuth({ login });
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(screen.getByLabelText("Email"), "owner@example.com");
    await user.type(screen.getByLabelText("Password"), "wrong-password");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password");
    expect(screen.queryByText("Dashboard landing")).not.toBeInTheDocument();
  });
});
