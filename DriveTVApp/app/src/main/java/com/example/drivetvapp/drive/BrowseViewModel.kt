package com.example.drivetvapp.drive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class BrowseState {
    data object Loading : BrowseState()
    data class FileList(val files: List<DriveFile>, val folderName: String) : BrowseState()
    data class Error(val message: String) : BrowseState()
}

class BrowseViewModel(private val driveRepository: DriveRepository) : ViewModel() {

    private val _browseState = MutableStateFlow<BrowseState>(BrowseState.Loading)
    val browseState: StateFlow<BrowseState> = _browseState

    private val folderStack = mutableListOf<Pair<String, String>>()
    
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack

    init {
        loadFiles()
    }

    fun loadFiles(folderId: String = "root", folderName: String = "My Drive") {
        _browseState.value = BrowseState.Loading

        viewModelScope.launch {
            try {
                val files = driveRepository.listFiles(folderId)
                _browseState.value = BrowseState.FileList(files, folderName)
            } catch (e: Exception) {
                _browseState.value = BrowseState.Error(e.message ?: "Failed to load files")
            }
        }
    }

    fun navigateToFolder(folderId: String, folderName: String) {
        val currentState = _browseState.value
        if (currentState is BrowseState.FileList) {
            folderStack.add(folderId to currentState.folderName)
            _canGoBack.value = folderStack.isNotEmpty()
        }
        loadFiles(folderId, folderName)
    }

    fun goBack(): Boolean {
        if (folderStack.isEmpty()) return false
        val (parentId, parentName) = folderStack.removeAt(folderStack.lastIndex)
        _canGoBack.value = folderStack.isNotEmpty()
        loadFiles(parentId, parentName)
        return true
    }

    fun getStreamUrl(fileId: String): String = driveRepository.getStreamUrl(fileId)

    fun getSubtitleUrls(fileId: String): List<String> {
        val currentState = _browseState.value as? BrowseState.FileList ?: return emptyList()
        val videoFile = currentState.files.find { it.id == fileId } ?: return emptyList()
        
        return currentState.files
            .filter { it.isSubtitle && it.nameWithoutExtension.contains(videoFile.nameWithoutExtension, ignoreCase = true) }
            .map { driveRepository.getStreamUrl(it.id) }
    }

    fun getAccessToken(): String = driveRepository.getAccessToken()
}
