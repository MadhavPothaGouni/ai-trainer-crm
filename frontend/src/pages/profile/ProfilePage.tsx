import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { changePassword } from "../../api/auth";
import { updateMyProfile } from "../../api/users";
import { useAuth } from "../../auth/useAuth";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  changePasswordFormSchema,
  profileFormSchema,
  type ChangePasswordFormValues,
  type ProfileFormValues,
} from "../../lib/validation";

/** "My profile" settings: the self-service half of UserController - PATCH /users/me and
 * POST /auth/change-password, both of which the typed API client already had (see
 * api/users.ts, api/auth.ts) but nothing on the frontend called until this page. */
export default function ProfilePage() {
  const { user, refreshUser, logout } = useAuth();
  const navigate = useNavigate();

  const [profileSuccess, setProfileSuccess] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const {
    register: registerProfile,
    handleSubmit: handleProfileSubmit,
    setError: setProfileFieldError,
    formState: { errors: profileErrors, isSubmitting: isSavingProfile },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileFormSchema),
    // Safe to read `user` directly here (not via useEffect+reset): this page only ever
    // mounts inside <ProtectedRoute>, which already guarantees a loaded user.
    defaultValues: {
      firstName: user?.firstName ?? "",
      lastName: user?.lastName ?? "",
      phone: user?.phone ?? "",
      timezone: user?.timezone ?? "",
      locale: user?.locale ?? "",
    },
  });

  const onSaveProfile = handleProfileSubmit(async (values) => {
    setProfileError(null);
    setProfileSuccess(false);
    try {
      await updateMyProfile({
        firstName: values.firstName,
        lastName: values.lastName,
        phone: blankToUndefined(values.phone),
        timezone: blankToUndefined(values.timezone),
        locale: blankToUndefined(values.locale),
      });
      await refreshUser();
      setProfileSuccess(true);
    } catch (error) {
      setProfileError(applyServerErrors(error, setProfileFieldError));
    }
  });

  const [passwordError, setPasswordError] = useState<string | null>(null);
  const {
    register: registerPassword,
    handleSubmit: handlePasswordSubmit,
    setError: setPasswordFieldError,
    formState: { errors: passwordErrors, isSubmitting: isChangingPassword },
  } = useForm<ChangePasswordFormValues>({ resolver: zodResolver(changePasswordFormSchema) });

  const onChangePassword = handlePasswordSubmit(async (values) => {
    setPasswordError(null);
    try {
      await changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword });
      // Changing password revokes every refresh token for this account server-side
      // (AuthService#changePassword) - this session's own refresh token is included,
      // so the next silent refresh would fail anyway. Sign out now, on our own terms,
      // rather than leave a session that's about to be kicked out mid-task.
      await logout();
      navigate("/login", { state: { passwordChanged: true } });
    } catch (error) {
      setPasswordError(applyServerErrors(error, setPasswordFieldError));
    }
  });

  if (!user) return null;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">My profile</h1>
        <p className="mt-1 text-sm text-slate-500">Update your personal details and password.</p>
      </div>

      <form
        onSubmit={onSaveProfile}
        noValidate
        className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6"
      >
        <h2 className="text-sm font-medium text-slate-500">Details</h2>
        {profileError && <Alert variant="error">{profileError}</Alert>}
        {profileSuccess && <Alert variant="success">Profile updated.</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="First name" error={profileErrors.firstName?.message} {...registerProfile("firstName")} />
          <TextField label="Last name" error={profileErrors.lastName?.message} {...registerProfile("lastName")} />
        </div>

        <TextField label="Email" value={user.email} disabled readOnly />

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Phone" error={profileErrors.phone?.message} {...registerProfile("phone")} />
          <TextField
            label="Timezone"
            placeholder="UTC"
            error={profileErrors.timezone?.message}
            {...registerProfile("timezone")}
          />
          <TextField
            label="Locale"
            placeholder="en-US"
            error={profileErrors.locale?.message}
            {...registerProfile("locale")}
          />
        </div>

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSavingProfile}>
            Save changes
          </Button>
        </div>
      </form>

      <form
        onSubmit={onChangePassword}
        noValidate
        className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6"
      >
        <h2 className="text-sm font-medium text-slate-500">Change password</h2>
        {passwordError && <Alert variant="error">{passwordError}</Alert>}
        <p className="text-sm text-slate-500">
          Changing your password signs you out of every session, including this one.
        </p>

        <TextField
          label="Current password"
          type="password"
          autoComplete="current-password"
          error={passwordErrors.currentPassword?.message}
          {...registerPassword("currentPassword")}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="New password"
            type="password"
            autoComplete="new-password"
            error={passwordErrors.newPassword?.message}
            {...registerPassword("newPassword")}
          />
          <TextField
            label="Confirm new password"
            type="password"
            autoComplete="new-password"
            error={passwordErrors.confirmPassword?.message}
            {...registerPassword("confirmPassword")}
          />
        </div>

        <div className="flex justify-end">
          <Button type="submit" variant="danger" isLoading={isChangingPassword}>
            Change password
          </Button>
        </div>
      </form>
    </div>
  );
}
